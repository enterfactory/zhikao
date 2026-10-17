package com.zhikao.modules.crawler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhikao.common.JsonHelper;
import com.zhikao.config.AsyncConfig;
import com.zhikao.entity.CrawlQuestion;
import com.zhikao.entity.CrawlRaw;
import com.zhikao.entity.CrawlSource;
import com.zhikao.entity.CrawlTask;
import com.zhikao.mapper.CrawlQuestionMapper;
import com.zhikao.mapper.CrawlRawMapper;
import com.zhikao.mapper.CrawlSourceMapper;
import com.zhikao.mapper.CrawlTaskMapper;
import com.zhikao.modules.crawler.extract.ExtractTrace;
import com.zhikao.modules.crawler.classify.ClassifierTagger;
import com.zhikao.modules.crawler.clean.CleanResult;
import com.zhikao.modules.crawler.clean.Cleaner;
import com.zhikao.modules.crawler.fetch.DocumentFetcher;
import com.zhikao.modules.crawler.fetch.FetchedItem;
import com.zhikao.modules.crawler.image.ImageMirrorService;
import com.zhikao.modules.crawler.parse.ParseOutcome;
import com.zhikao.modules.crawler.parse.ParsedQuestion;
import com.zhikao.modules.crawler.parse.ParserRegistry;
import com.zhikao.modules.crawler.quality.DocQuality;
import com.zhikao.modules.crawler.quality.DocQualityEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 流水线执行器：跑「采集→解析→清洗→打标→待审」整条链路，把文档逐层推进成候选题目。
 *
 * <p>之所以独立成 @Async Bean 而不是 Service 的方法自调用：Spring 的 @Async 靠代理生效，
 * 同一个 Bean 内部方法自调用不会走代理，异步就失效了。把耗时的抽取/OCR 放这里、
 * Service 只负责建任务与立即返回 taskId，是「异步必须跨 Bean」这条约束的标准解法
 * ——一张扫描页的 OCR 是十秒级的，几百页的卷子必须让调用方立刻脱手。
 *
 * <p>落库节奏：一份文档 = 一行 crawl_raw（dedup_hash 唯一，重复采集直接跳过，天然幂等）；
 * 一条解析产物经清洗通过后 = 一行 crawl_question（直接推进到 TAGGED 待审阶段）。
 * 解析失败不丢弃：原文已留在 crawl_raw，补解析器后可重跑。
 *
 * <p>{@code via=OCR} 的产物会给候选题追加 {@code OCR_TEXT} 质量标记：
 * 同一个「看起来没问题」的题干，文本层来的和 OCR 来的，人审该投入的注意力不一样。
 */
@Component
public class CrawlerPipelineRunner {

    private static final Logger log = LoggerFactory.getLogger(CrawlerPipelineRunner.class);

    private final CrawlTaskMapper taskMapper;
    private final CrawlSourceMapper sourceMapper;
    private final CrawlRawMapper rawMapper;
    private final CrawlQuestionMapper questionMapper;
    private final DocumentFetcher documentFetcher;
    private final ParserRegistry parserRegistry;
    private final Cleaner cleaner;
    private final ClassifierTagger classifierTagger;
    private final ImageMirrorService imageMirrorService;

    private final com.zhikao.mapper.FileBlobMapper contentLocks;
    private final DocumentVersions versions;
    private final SourceLocator locator;
    private final CrawlTaskLease leases;
    private final org.springframework.transaction.support.TransactionTemplate documentTransaction;

    public CrawlerPipelineRunner(CrawlTaskMapper taskMapper, CrawlSourceMapper sourceMapper,
                                 CrawlRawMapper rawMapper, CrawlQuestionMapper questionMapper,
                                 DocumentFetcher documentFetcher, ParserRegistry parserRegistry,
                                 Cleaner cleaner, ClassifierTagger classifierTagger,
                                 ImageMirrorService imageMirrorService, CrawlTaskLease leases, org.springframework.transaction.PlatformTransactionManager tx, DocumentVersions versions, SourceLocator locator,com.zhikao.mapper.FileBlobMapper contentLocks) {
        this.contentLocks=contentLocks;
        this.versions=versions;this.locator=locator;
        this.leases=leases;
        this.documentTransaction=new org.springframework.transaction.support.TransactionTemplate(tx);
        this.taskMapper = taskMapper;
        this.sourceMapper = sourceMapper;
        this.rawMapper = rawMapper;
        this.questionMapper = questionMapper;
        this.documentFetcher = documentFetcher;
        this.parserRegistry = parserRegistry;
        this.cleaner = cleaner;
        this.classifierTagger = classifierTagger;
        this.imageMirrorService = imageMirrorService;
    }

    /**
     * 异步执行整条流水线：Service 建好 PENDING 任务后立即返回，这里接手跑并回写进度/结果。
     *
     * <p>指定 {@code crawlExecutor} 而不是用默认执行器：默认是操作日志池（并发 2、队列 1000），
     * 采集任务一旦压进去，日志写入就要和十分钟一次的 OCR 抢线程。
     * 队列满时该池采用拒绝策略，异常由提交方（{@code CrawlerServiceImpl#runTask}）转成业务提示。
     */
    @Async(AsyncConfig.CRAWL_EXECUTOR)
    public void run(Long taskId) {
        String leaseToken=leases.claim(taskId);
        if(leaseToken==null)return;
        CrawlTask task = taskMapper.selectById(taskId);
        if (task == null) {
            log.warn("采集任务不存在，跳过：taskId={}", taskId);
            return;
        }
        CrawlSource source = sourceMapper.selectById(task.getSourceId());
        if (source == null) {
            finishFailed(task, leaseToken, "来源不存在：" + task.getSourceId());
            leases.release(taskId,leaseToken);
            return;
        }
        int fetched = 0;
        int cleaned = 0;
        int staged = 0;
        try {
            List<FetchedItem> items = documentFetcher.fetch(source, path -> alreadyIngested(path,source.getDefaultSubjectId()));
            // 任务级内容去重集合：同一次运行里相同归一化内容只留一条
            Set<String> seenHash = new HashSet<>();
            for (FetchedItem item : items) {
                int[] counts = documentTransaction.execute(status -> {
                    leases.fence(taskId,leaseToken);
                    CrawlRaw raw=upsertRaw(taskId,item,source.getDefaultSubjectId());
                    if(raw==null)return new int[]{0,0,0};
                    int[] result=processRaw(raw,source,item,seenHash);
                    leases.fence(taskId,leaseToken);
                    return new int[]{1,result[0],result[1]};
                });
                fetched+=counts[0];cleaned+=counts[1];staged+=counts[2];
            }
            // A recovered task includes documents committed by its previous worker.
            var committed = rawMapper.selectList(Wrappers.<CrawlRaw>lambdaQuery()
                    .eq(CrawlRaw::getTaskId, taskId));
            fetched = committed.size();
            staged = committed.stream().mapToInt(r -> r.getStagedCount() == null ? 0 : r.getStagedCount()).sum();
            cleaned = staged;
            task.setFetched(fetched);
            task.setCleaned(cleaned);
            task.setStaged(staged);
            task.setStatus("SUCCESS");
            task.setFinishedAt(LocalDateTime.now());
            // 成功但一份新文档都没取到：把原因写进 error，面板上看得见，不用去翻日志
            if (fetched == 0) {
                task.setError("没有新文档需要采集（目录内文档此前已采过，或均未取出文字）");
            } else if (staged == 0) {
                // 「文档采到了、题一道都没出」是过去最难查的一种失败：任务绿勾、计数全 0。
                // 这里只给批次级提示，逐份文档的原因在 crawl_raw 的解析列（前端「解析日志」页签）
                task.setError(fetched + " 份文档已取字，但没有题目进入待审（逐文档原因见「解析日志」）");
            }
            leases.fence(taskId,leaseToken);
            taskMapper.update(null,Wrappers.<CrawlTask>lambdaUpdate().eq(CrawlTask::getId,taskId).eq(CrawlTask::getLeaseOwner,leaseToken).set(CrawlTask::getStatus,task.getStatus()).set(CrawlTask::getFetched,fetched).set(CrawlTask::getCleaned,cleaned).set(CrawlTask::getStaged,staged).set(CrawlTask::getFinishedAt,task.getFinishedAt()).set(CrawlTask::getError,task.getError()));
            log.info("采集任务完成：taskId={} fetched={} cleaned={} staged={}", taskId, fetched, cleaned, staged);
        } catch (Exception e) {
            log.error("采集任务失败：taskId={} {}", taskId, e.getMessage(), e);
            task.setFetched(fetched);
            task.setCleaned(cleaned);
            task.setStaged(staged);
            try { leases.fence(taskId,leaseToken); finishFailed(task,leaseToken,e.getMessage()); } catch(Exception lost) { log.warn("任务已失去租约: {}",taskId); }
        } finally { leases.release(taskId,leaseToken); }
    }

    /** 处理一条原文：解析→逐条清洗打标→落待审表，并把「谁解析的、出了几题、为什么没题、质量分」写回文档行 */
    private int[] processRaw(CrawlRaw raw, CrawlSource source, FetchedItem item, Set<String> seenHash) {
        boolean viaOcr = item.ocr();
        Float ocrScore = item.trace().ocrScore();
        ParseOutcome outcome = parserRegistry.parse(raw.getContent());
        List<ParsedQuestion> parsedList = outcome.parsed();
        int cleaned = 0;
        int staged = 0;
        java.util.List<CrawlQuestion> located=new java.util.ArrayList<>();
        for (ParsedQuestion parsed : parsedList) {
            CleanResult clean = cleaner.clean(parsed, seenHash);
            if (!clean.isPassed() || clean.getQualityFlags().contains(Cleaner.FLAG_DUPLICATE)) {
                continue; // 结构性硬伤（题干过短）不入待审
            }
            // OCR 产物带概率误差（可能把「己」认成「已」、把 1 认成 l），标记出来让人审重点看
            if (viaOcr) {
                clean.getQualityFlags().add(Cleaner.FLAG_OCR_TEXT);
            }
            // 富文本配图镜像：把 http(s) 外链与本地相对路径的 <img src> 搬进文件库并改写成本系统地址。
            // 失败只追加 IMG_MIRROR_FAILED 标记并保留原 URL——绝不因为一张图搬不动就把整道题丢掉。
            if (imageMirrorService.mirror(clean, source, raw.getSourceUrl())) {
                clean.getQualityFlags().add(Cleaner.FLAG_IMG_MIRROR_FAILED);
            }
            ClassifierTagger.Tags tags = classifierTagger.tag(clean, parsed, source.getDefaultSubjectId());
            CrawlQuestion question = CrawlQuestion.builder()
                    .rawId(raw.getId())
                    .stage("TAGGED") // 清洗+打标一体完成，直接进待审
                    .grade(tags.grade())
                    .gradeLevel(tags.gradeLevel())
                    .subjectId(tags.subjectId())
                    .questionType(clean.getQuestionType())
                    .content(clean.getContent())
                    .options(JsonHelper.toJson(clean.getOptions()))
                    .answer(clean.getAnswer())
                    .analysis(clean.getAnalysis())
                    .difficulty(tags.difficulty())
                    .knowledgeTags(tags.knowledgeTags())
                    .qualityFlags(clean.getQualityFlags().isEmpty() ? null : String.join(",", clean.getQualityFlags()))
                    .cleanTime(LocalDateTime.now())
                    .createTime(LocalDateTime.now())
                    .build();
            located.add(question);
            cleaned++;
            staged++;
        }
        locator.locateAll(raw,located);
        for(var question:located) {
            var prediction=new java.util.LinkedHashMap<String,Object>();
            prediction.put("content",question.getContent());prediction.put("options",JsonHelper.toOptionMap(question.getOptions()));
            prediction.put("answer",question.getAnswer());prediction.put("analysis",question.getAnalysis());
            prediction.put("question_type",question.getQuestionType());prediction.put("page",question.getSourcePage());
            question.setPredictionJson(JsonHelper.toJson(prediction));questionMapper.insert(question);
        }
        if(raw.getPageCount()!=null)rawMapper.update(null,Wrappers.<CrawlRaw>lambdaUpdate().eq(CrawlRaw::getId,raw.getId()).set(CrawlRaw::getPageCount,raw.getPageCount()));
        writeParseTrace(raw, outcome, staged, viaOcr, ocrScore, item.trace());
        return new int[]{cleaned, staged};
    }

    /**
     * 把本次解析结论落到文档行（管理页「解析日志」读的就是这几列）。
     *
     * <p>只发 id + 解析列，不带 content：crawl_raw.content 是 MEDIUMTEXT，
     * 用整条实体 update 会把几十 KB 正文再写一遍，纯属浪费。
     *
     * <p>质量分与置信度在同一行落：它们是<b>同一次识别的产物</b>，
     * 分两处写就会出现「解析列是新的、质量列是旧的」这种没法解释的中间态。
     *
     * <p>取字层的自述（哪条通道干的、公式片段逐层认出几处）在这里一起落：
     * 一份「题干少一个式子」的文档，结构化指标全都满分也发现不了——
     * 缺的东西不在文本里，只有抽取层知道自己丢过什么。
     */
    private void writeParseTrace(CrawlRaw raw, ParseOutcome outcome, int staged, boolean viaOcr,
                                 Float ocrScore, ExtractTrace trace) {
        DocQuality quality = DocQualityEvaluator.evaluate(outcome.parsed(), staged, viaOcr, ocrScore);
        CrawlRaw patch = new CrawlRaw();
        patch.setId(raw.getId());
        patch.setParserName(outcome.parserName());
        patch.setParsedCount(outcome.parsedCount());
        patch.setStagedCount(staged);
        patch.setParseNote(truncateText(buildNote(outcome, staged)));
        patch.setQualityScore(quality.score());
        patch.setQualityDetail(truncateText(withExtractNote(quality.detail(), trace.note())));
        patch.setOcrConfidence(ocrScore == null ? null : ocrScore.doubleValue());
        // 分层识别计数（S2）：一句话说明之外再落成可 group by 的列，
        // 「多少文档本地就兜住、多少必须花钱升云端」问的是列，不是那行字
        patch.setOcrChannels(trace.channels());
        patch.setFormulaCandidates(trace.formulaCandidates());
        patch.setFormulaRecovered(trace.formulaRecovered());
        patch.setFormulaLocal(trace.formulaLocal());
        patch.setFormulaCloud(trace.formulaCloud());
        patch.setFormulaCloudCalls(trace.formulaCloudCalls());
        rawMapper.updateById(patch);
    }

    private String withExtractNote(String detail, String extractNote) {
        if (extractNote == null || extractNote.isBlank()) {
            return detail;
        }
        return detail + " · " + extractNote;
    }

    /** 结论说明：正常出齐题时留空（页面显示「-」），只在有信息缺失时才占一栏 */
    private String buildNote(ParseOutcome outcome, int staged) {
        if (outcome.parsedCount() == 0) {
            return outcome.note();
        }
        if (staged == 0) {
            return "解析出 " + outcome.parsedCount() + " 道，清洗后无一通过（题干过短或与本批已有题目重复）";
        }
        if (staged < outcome.parsedCount()) {
            return "解析出 " + outcome.parsedCount() + " 道，" + staged + " 道进入待审（其余为题干过短或重复）";
        }
        return null;
    }

    private String truncateText(String note) {
        if (note == null) {
            return null;
        }
        return note.length() > 480 ? note.substring(0, 480) : note;
    }

    /** 原文落库：dedup_hash 已存在则返回 null（跳过），否则插入并返回 */
    private CrawlRaw upsertRaw(Long taskId, FetchedItem item,Long subjectId) {
        String contentHash=item.contentHash()==null?versions.contentHash(java.nio.file.Path.of(item.sourceUrl())):item.contentHash();
        String hash=versions.identity(contentHash,subjectId);
        contentLocks.lockContent("document:"+hash);
        if (existsDedupHash(hash)) {
            return null;
        }
        CrawlRaw raw = CrawlRaw.builder()
                .taskId(taskId)
                .sourceUrl(item.sourceUrl())
                .dedupHash(hash).contentHash(contentHash).parserVersion(versions.fingerprint())
                .sourceName(java.nio.file.Path.of(item.sourceUrl()).getFileName().toString()).sourceSnapshot(item.snapshotPath()).extractionMillis(item.extractionMillis())
                .content(item.content())
                .charCount(item.content() == null ? 0 : item.content().length())
                .viaOcr(item.ocr() ? 1 : 0)
                .fetchTime(LocalDateTime.now())
                .build();
        rawMapper.insert(raw);
        return raw;
    }

    /** 采集前的预检：这份文档（按文件定位）是否已经入过原文表——命中就不必再花时间抽字/OCR */
    private boolean alreadyIngested(String sourceUrl,Long subjectId) {
        return existsDedupHash(versions.identity(versions.contentHash(java.nio.file.Path.of(sourceUrl)),subjectId));
    }

    private boolean existsDedupHash(String dedupHash) {
        return rawMapper.selectCount(Wrappers.<CrawlRaw>lambdaQuery()
                .eq(CrawlRaw::getDedupHash, dedupHash)) > 0;
    }

    private void finishFailed(CrawlTask task, String token, String error) {
        taskMapper.update(null, Wrappers.<CrawlTask>lambdaUpdate()
                .eq(CrawlTask::getId, task.getId()).eq(CrawlTask::getLeaseOwner, token)
                .eq(CrawlTask::getStatus, "RUNNING").ge(CrawlTask::getLeaseUntil, LocalDateTime.now())
                .set(CrawlTask::getStatus, "FAILED")
                .set(CrawlTask::getError, error == null ? "未知错误" : truncate(error))
                .set(CrawlTask::getFinishedAt, LocalDateTime.now()));
    }

    private String truncate(String text) {
        return text.length() > 1000 ? text.substring(0, 1000) : text;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
