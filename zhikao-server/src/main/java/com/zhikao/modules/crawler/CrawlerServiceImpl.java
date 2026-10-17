package com.zhikao.modules.crawler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhikao.common.BusinessException;
import com.zhikao.common.ImportResultVO;
import com.zhikao.common.JsonHelper;
import com.zhikao.common.PageResult;
import com.zhikao.common.QuestionTypeEnum;
import com.zhikao.config.CrawlerProperties;
import com.zhikao.entity.CrawlQuestion;
import com.zhikao.entity.CrawlRaw;
import com.zhikao.entity.CrawlSource;
import com.zhikao.entity.CrawlTask;
import com.zhikao.entity.Subject;
import com.zhikao.mapper.CrawlQuestionMapper;
import com.zhikao.mapper.CrawlRawMapper;
import com.zhikao.mapper.CrawlSourceMapper;
import com.zhikao.mapper.CrawlTaskMapper;
import com.zhikao.mapper.SubjectMapper;
import com.zhikao.modules.crawler.dto.CrawlDocumentQueryDTO;
import com.zhikao.modules.crawler.dto.CrawlImportLocalDTO;
import com.zhikao.modules.crawler.dto.CrawlQuestionQueryDTO;
import com.zhikao.modules.crawler.dto.CrawlReviewDTO;
import com.zhikao.modules.crawler.dto.CrawlSourceSaveDTO;
import com.zhikao.modules.crawler.dto.CrawlTaskQueryDTO;
import com.zhikao.modules.crawler.fetch.LocalPathGuard;
import com.zhikao.modules.crawler.extract.ExtractorRegistry;
import com.zhikao.modules.crawler.ocr.OcrService;
import com.zhikao.modules.crawler.promote.Promoter;
import com.zhikao.modules.crawler.vo.CrawlDocumentVO;
import com.zhikao.modules.crawler.vo.CrawlQuestionVO;
import com.zhikao.modules.crawler.vo.CrawlSourceVO;
import com.zhikao.modules.crawler.vo.CrawlOcrSummaryVO;
import com.zhikao.modules.crawler.vo.CrawlStatsVO;
import com.zhikao.modules.crawler.vo.CrawlTaskVO;
import com.zhikao.modules.crawler.vo.CrawlUploadVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

/**
 * 题库文档采集与清洗服务实现（六期建模块，七期改造）。
 *
 * <p>七期把「HTTP 网页爬虫」整条路线删掉，采集入口收敛成两个，且<b>共用同一条流水线</b>：
 * <ul>
 *   <li>本地目录（LOCAL_DIR）：直接指向一个放满 docx/pdf/图片/文本的目录；</li>
 *   <li>文档上传：文件落进摄取目录（也是 LOCAL_DIR 源）后，走完全一样的抽取→解析→清洗→打标链路。</li>
 * </ul>
 * 「上传」没有第二套采集代码，是这次重构最想保住的性质：入口再多，落库路径只有一条。
 */
@Service
public class CrawlerServiceImpl implements CrawlerService {

    /** stats 里固定列出的阶段，保证面板即使某阶段为 0 也有格子 */
    private static final List<String> STAGES =
            List.of("RAW", "PARSED", "CLEANED", "TAGGED", "REVIEWED", "PROMOTED", "REJECTED");

    /** 单次上传的文件数上限：几十份文档一次传上来，任务会跑到分钟级，不如分批发，反馈更清楚 */
    private static final int MAX_UPLOAD_FILES = 20;

    /** 摄取目录按天分层：/yyyyMMdd/ —— 让「哪天传了什么」在磁盘上一眼可查 */
    private static final DateTimeFormatter DAY_DIR = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 文档详情里的原文预览长度：够人工判断取字质量（OCR 错位/乱码），又不至于把整份卷子塞进弹窗 */
    private static final int PREVIEW_CHARS = 3000;

    /** 文档详情最多列出的候选题条数：一份试卷通常 20~30 题，超出部分以截断标记提示去待审页看 */
    private static final int MAX_DETAIL_QUESTIONS = 50;

    private final CrawlSourceMapper sourceMapper;
    private final CrawlTaskMapper taskMapper;
    private final CrawlRawMapper rawMapper;
    private final CrawlQuestionMapper questionMapper;
    private final SubjectMapper subjectMapper;
    private final CrawlerPipelineRunner pipelineRunner;
    private final com.zhikao.modules.curation.CurationService curation;
    private final LocalPathGuard pathGuard;
    private final ExtractorRegistry extractors;
    private final CrawlerProperties properties;
    private final OcrService ocrService;

    public CrawlerServiceImpl(CrawlSourceMapper sourceMapper, CrawlTaskMapper taskMapper,
                              CrawlRawMapper rawMapper, CrawlQuestionMapper questionMapper,
                              SubjectMapper subjectMapper, CrawlerPipelineRunner pipelineRunner,
                              com.zhikao.modules.curation.CurationService curation, LocalPathGuard pathGuard,
                              ExtractorRegistry extractors, CrawlerProperties properties,
                              OcrService ocrService) {
        this.sourceMapper = sourceMapper;
        this.taskMapper = taskMapper;
        this.rawMapper = rawMapper;
        this.questionMapper = questionMapper;
        this.subjectMapper = subjectMapper;
        this.pipelineRunner = pipelineRunner;
        this.curation = curation;
        this.pathGuard = pathGuard;
        this.extractors = extractors;
        this.properties = properties;
        this.ocrService = ocrService;
    }


    // ==================== 来源管理 ====================

    @Override
    public Long saveSource(CrawlSourceSaveDTO dto) {
        String type = normalizeSourceType(dto.getSourceType());
        // 建源即校验白名单，早失败早暴露，不等跑任务时才发现越权
        pathGuard.requireAllowed(dto.getBaseUrl());
        CrawlSource entity = CrawlSource.builder()
                .id(dto.getId())
                .name(dto.getName().trim())
                .baseUrl(dto.getBaseUrl().trim())
                .sourceType(type)
                .licenseNote(dto.getLicenseNote())
                .rateLimitMs(dto.getRateLimitMs() == null ? 1000 : Math.max(0, dto.getRateLimitMs()))
                .defaultSubjectId(dto.getDefaultSubjectId())
                .enabled(dto.getEnabled() == null ? 1 : dto.getEnabled())
                .remark(dto.getRemark())
                .build();
        if (dto.getId() == null) {
            entity.setCreateTime(LocalDateTime.now());
            sourceMapper.insert(entity);
        } else {
            if (sourceMapper.selectById(dto.getId()) == null) {
                throw new BusinessException("来源不存在");
            }
            sourceMapper.updateById(entity);
        }
        return entity.getId();
    }

    @Override
    public void toggleSource(Long id) {
        CrawlSource source = sourceMapper.selectById(id);
        if (source == null) {
            throw new BusinessException("来源不存在");
        }
        source.setEnabled(source.getEnabled() != null && source.getEnabled() == 1 ? 0 : 1);
        sourceMapper.updateById(source);
    }

    @Override
    public Integer sourceEnabled(Long id) {
        CrawlSource source = sourceMapper.selectById(id);
        if (source == null) {
            throw new BusinessException("来源不存在");
        }
        return source.getEnabled();
    }

    /**
     * 删除来源：只删「配置」，不连带删事实（历史任务、已抓原文、待审题都保留）。
     * 进行中的任务不允许删来源——抓取线程正拿着这份配置在跑，删了会让它失去归属。
     */
    @Override
    public void deleteSource(Long id) {
        if (sourceMapper.selectById(id) == null) {
            throw new BusinessException("来源不存在");
        }
        long active = taskMapper.selectCount(Wrappers.<CrawlTask>lambdaQuery()
                .eq(CrawlTask::getSourceId, id)
                .in(CrawlTask::getStatus, "PENDING", "RUNNING"));
        if (active > 0) {
            throw new BusinessException("该来源有进行中的采集任务，请等待任务结束后再删除");
        }
        sourceMapper.deleteById(id);
    }

    @Override
    public List<CrawlSourceVO> listSources() {
        Map<Long, String> subjectNames = subjectNames();
        return sourceMapper.selectList(Wrappers.<CrawlSource>lambdaQuery().orderByAsc(CrawlSource::getId))
                .stream().map(source -> toSourceVO(source, subjectNames)).collect(Collectors.toList());
    }

    // ==================== 任务 ====================

    @Override
    public Long runTask(Long sourceId) {
        CrawlSource source = sourceMapper.selectById(sourceId);
        if (source == null) {
            throw new BusinessException("来源不存在");
        }
        if (source.getEnabled() != null && source.getEnabled() == 0) {
            throw new BusinessException("来源已停用，无法发起抓取");
        }
        CrawlTask task = CrawlTask.builder()
                .sourceId(sourceId)
                .status("PENDING")
                .fetched(0).cleaned(0).staged(0)
                .build();
        taskMapper.insert(task);
        // 立即返回 taskId；耗时的 fetch→parse→clean→tag 交给采集线程池（@Async("crawlExecutor")）。
        // 队列满时 Spring 抛 TaskRejectedException：这里把任务标失败并回一句人话，
        // 绝不退回「让提交线程自己跑」——那等于把分钟级的 OCR 压到当前 HTTP 线程上。
        try {
            pipelineRunner.run(task.getId());
        } catch (RejectedExecutionException e) {
            // Keep durable PENDING row. Recovery dispatcher retries when capacity is available.

        }
        return task.getId();
    }

    @Override
    public PageResult<CrawlTaskVO> taskPage(CrawlTaskQueryDTO query) {
        LambdaQueryWrapper<CrawlTask> wrapper = Wrappers.<CrawlTask>lambdaQuery()
                .eq(query.getSourceId() != null, CrawlTask::getSourceId, query.getSourceId())
                .eq(StringUtils.hasText(query.getStatus()), CrawlTask::getStatus, query.getStatus())
                .orderByDesc(CrawlTask::getId);
        Page<CrawlTask> page = taskMapper.selectPage(
                new Page<>(query.safePageNum(), query.safePageSize()), wrapper);
        Map<Long, String> sourceNames = sourceNames();
        return PageResult.of(page, task -> {
            CrawlTaskVO vo = new CrawlTaskVO();
            vo.setId(task.getId());
            vo.setSourceId(task.getSourceId());
            vo.setSourceName(sourceNames.get(task.getSourceId()));
            vo.setStatus(task.getStatus());
            vo.setFetched(task.getFetched());
            vo.setCleaned(task.getCleaned());
            vo.setStaged(task.getStaged());
            vo.setError(task.getError());
            vo.setStartedAt(task.getStartedAt());
            vo.setFinishedAt(task.getFinishedAt());
            return vo;
        });
    }

    // ==================== 文档解析日志 ====================

    @Override
    public PageResult<CrawlDocumentVO> documentPage(CrawlDocumentQueryDTO query) {
        LambdaQueryWrapper<CrawlRaw> wrapper = Wrappers.<CrawlRaw>lambdaQuery()
                .select(CrawlRaw::getId, CrawlRaw::getTaskId, CrawlRaw::getSourceUrl,
                        CrawlRaw::getCharCount, CrawlRaw::getViaOcr, CrawlRaw::getParserName,
                        CrawlRaw::getParsedCount, CrawlRaw::getStagedCount, CrawlRaw::getParseNote,
                        CrawlRaw::getQualityScore, CrawlRaw::getQualityDetail, CrawlRaw::getOcrConfidence,
                        CrawlRaw::getOcrChannels, CrawlRaw::getFormulaCandidates, CrawlRaw::getFormulaRecovered,
                        CrawlRaw::getFormulaLocal, CrawlRaw::getFormulaCloud, CrawlRaw::getFormulaCloudCalls,
                        CrawlRaw::getFetchTime,CrawlRaw::getContentHash,CrawlRaw::getParserVersion,CrawlRaw::getSourceName,CrawlRaw::getPageCount)
                .eq(query.getTaskId() != null, CrawlRaw::getTaskId, query.getTaskId())
                .like(StringUtils.hasText(query.getKeyword()), CrawlRaw::getSourceUrl, query.getKeyword())
                .eq(Boolean.TRUE.equals(query.getUnresolvedOnly()), CrawlRaw::getStagedCount, 0)
                .orderByDesc(CrawlRaw::getId);
        Page<CrawlRaw> page = rawMapper.selectPage(
                new Page<>(query.safePageNum(), query.safePageSize()), wrapper);
        return PageResult.of(page, this::toDocumentVO);
    }

    @Override
    public CrawlDocumentVO documentDetail(Long id) {
        CrawlRaw raw = rawMapper.selectById(id);
        if (raw == null) {
            throw new BusinessException("文档解析记录不存在：" + id);
        }
        CrawlDocumentVO vo = toDocumentVO(raw);
        String content = raw.getContent() == null ? "" : raw.getContent();
        vo.setContentLength(content.length());
        vo.setContentTruncated(content.length() > PREVIEW_CHARS);
        vo.setContentPreview(content.substring(0, (int) Math.min(content.length(), PREVIEW_CHARS)));
        List<CrawlQuestion> related = questionMapper.selectList(Wrappers.<CrawlQuestion>lambdaQuery()
                .eq(CrawlQuestion::getRawId, id)
                .orderByAsc(CrawlQuestion::getId));
        Map<Long, String> subjectNames = subjectNames();
        vo.setQuestionsTruncated(related.size() > MAX_DETAIL_QUESTIONS);
        vo.setQuestions(related.stream().limit(MAX_DETAIL_QUESTIONS)
                .map(q -> toQuestionVO(q, subjectNames)).collect(Collectors.toList()));
        return vo;
    }

    // ==================== 待审题目 ====================

    @Override
    public PageResult<CrawlQuestionVO> questionPage(CrawlQuestionQueryDTO query) {
        LambdaQueryWrapper<CrawlQuestion> wrapper = Wrappers.<CrawlQuestion>lambdaQuery()
                .eq(StringUtils.hasText(query.getStage()), CrawlQuestion::getStage, query.getStage())
                .eq(query.getSubjectId() != null, CrawlQuestion::getSubjectId, query.getSubjectId())
                .eq(StringUtils.hasText(query.getGrade()), CrawlQuestion::getGrade, query.getGrade())
                .eq(query.getRawId() != null, CrawlQuestion::getRawId, query.getRawId())
                .orderByDesc(CrawlQuestion::getId);
        Page<CrawlQuestion> page = questionMapper.selectPage(
                new Page<>(query.safePageNum(), query.safePageSize()), wrapper);
        Map<Long, String> subjectNames = subjectNames();
        return PageResult.of(page, question -> toQuestionVO(question, subjectNames));
    }

    @Override
    public CrawlQuestionVO questionDetail(Long id) {
        CrawlQuestion question = questionMapper.selectById(id);
        if (question == null) {
            throw new BusinessException("采集题目不存在：" + id);
        }
        return toQuestionVO(question, subjectNames());
    }

    @Override
    public ImportResultVO review(CrawlReviewDTO dto) {
        String action = dto.getAction() == null ? "" : dto.getAction().trim().toUpperCase();
        if (!"PROMOTE".equals(action) && !"REJECT".equals(action)) {
            throw new BusinessException("审核动作只能是 PROMOTE 或 REJECT");
        }
        ImportResultVO result=new ImportResultVO();
        for(Long id:dto.getIds()) {
            CrawlQuestion q=questionMapper.selectById(id);
            if(q==null){result.addError("候选题不存在："+id);continue;}
            try {
                if("PROMOTE".equals(action))curation.publish(id,q.getRevision(),dto.getNote(),dto.getSubjectId());
                else curation.reject(id,q.getRevision(),dto.getNote());
                result.addSuccess();
            } catch(BusinessException error){result.addError("第 "+id+" 条："+error.getMessage());}
        }
        return result;
    }

    // ==================== 本地导入 ====================

    @Override
    public Long importLocal(CrawlImportLocalDTO dto) {
        CrawlSource source;
        if (dto.getSourceId() != null) {
            source = sourceMapper.selectById(dto.getSourceId());
            if (source == null) {
                throw new BusinessException("指定的来源不存在");
            }
            if (!"LOCAL_DIR".equalsIgnoreCase(source.getSourceType())) {
                throw new BusinessException("本地导入只能针对 LOCAL_DIR 类型来源");
            }
            // 用了已登记源，就以它的 base_url 为准做白名单校验（dirPath 仅作前端展示/提示）
            pathGuard.requireAllowed(source.getBaseUrl());
        } else {
            // 未登记：校验目录合法后即时建一个 LOCAL_DIR 源（幂等：同目录复用已有源）
            var allowed = pathGuard.requireAllowed(dto.getDirPath());
            String normalizedPath = allowed.toString();
            source = sourceMapper.selectOne(Wrappers.<CrawlSource>lambdaQuery()
                    .eq(CrawlSource::getSourceType, "LOCAL_DIR")
                    .eq(CrawlSource::getBaseUrl, normalizedPath)
                    .last("limit 1"));
            if (source == null) {
                source = CrawlSource.builder()
                        .name("本地导入-" + allowed.getFileName())
                        .baseUrl(normalizedPath)
                        .sourceType("LOCAL_DIR")
                        .licenseNote("本地自有资源（演示数据）")
                        .rateLimitMs(0)
                        .enabled(1)
                        .createTime(LocalDateTime.now())
                        .build();
                sourceMapper.insert(source);
            }
        }
        return runTask(source.getId());
    }

    // ==================== 文档上传 ====================

    /**
     * 文档上传采集：先落盘再建任务。
     *
     * <p>落盘位置是「摄取目录 / yyyyMMdd / 原文件名」：按天分层、保留原名，
     * 出问题时能直接在磁盘上找到「老师那天传的那份卷子」，不是一堆 UUID。
     * 同名文件加时间戳后缀而不是覆盖——覆盖等于删掉上一次的采集现场。
     *
     * <p>不合规的文件（格式不认识、空文件、超限）逐个拒收并把原因回给前端，
     * 只有全部被拒才整体报错；一份混了杂文件的批量上传不该因为一张不相干的截图就全批失败。
     */
    @Override
    public CrawlUploadVO upload(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new BusinessException("请至少选择一个文档");
        }
        if (files.length > MAX_UPLOAD_FILES) {
            throw new BusinessException("单次最多上传 " + MAX_UPLOAD_FILES + " 个文档，当前 " + files.length + " 个");
        }
        Path ingestRoot = pathGuard.ensureIngestRoot();
        Path dayDir = ingestRoot.resolve(LocalDate.now().format(DAY_DIR));
        try {
            Files.createDirectories(dayDir);
        } catch (IOException e) {
            throw new BusinessException("摄取目录创建失败：" + e.getMessage());
        }
        CrawlUploadVO result = new CrawlUploadVO();
        result.setIngestDir(dayDir.toString());
        int stored = 0;
        for (MultipartFile file : files) {
            String fileName = safeFileName(file.getOriginalFilename());
            if (fileName == null) {
                result.getRejected().add("（文件名不合法）");
                continue;
            }
            if (file.isEmpty()) {
                result.getRejected().add(fileName + "：文件内容为空");
                continue;
            }
            if (extractors.select(fileName) == null) {
                result.getRejected().add(fileName + "：格式不支持（可用 docx/doc/pdf/图片/md/txt/json/jsonl/csv）");
                continue;
            }
            if (file.getSize() > properties.getMaxDocBytes()) {
                result.getRejected().add(fileName + "：超过单文件上限 "
                        + properties.getMaxDocBytes() / 1024 / 1024 + "MB");
                continue;
            }
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, uniqueTarget(dayDir, fileName));
                stored++;
            } catch (IOException e) {
                result.getRejected().add(fileName + "：落盘失败（" + e.getMessage() + "）");
            }
        }
        if (stored == 0) {
            throw new BusinessException("没有文档被接收：" + String.join("；", result.getRejected()));
        }
        result.setStored(stored);
        Long sourceId = ensureIngestSource(ingestRoot);
        result.setSourceId(sourceId);
        result.setTaskId(runTask(sourceId));
        return result;
    }

    /** 上传文档没有「自己的来源」就建一个：所有上传共用摄取目录这一个 LOCAL_DIR 源 */
    private Long ensureIngestSource(Path ingestRoot) {
        String location = ingestRoot.toString();
        CrawlSource existing = sourceMapper.selectOne(Wrappers.<CrawlSource>lambdaQuery()
                .eq(CrawlSource::getSourceType, "LOCAL_DIR")
                .eq(CrawlSource::getBaseUrl, location)
                .last("limit 1"));
        if (existing != null) {
            return existing.getId();
        }
        CrawlSource source = CrawlSource.builder()
                .name("文档上传（摄取目录）")
                .baseUrl(location)
                .sourceType("LOCAL_DIR")
                .licenseNote("上传者自备授权的教学文档与试卷")
                .rateLimitMs(0)
                .enabled(1)
                .createTime(LocalDateTime.now())
                .build();
        sourceMapper.insert(source);
        return source.getId();
    }

    /**
     * 只取文件名部分并做最小合法性检查：浏览器/客户端可能把整条路径带上来，
     * 这里绝不能照着它写盘（{@code ../} 就是越权读写的入口）。
     * 真正的落盘目录始终是服务端算出来的「摄取目录/当天」，文件名只做末尾一段。
     */
    private String safeFileName(String original) {
        if (original == null || original.isBlank()) {
            return null;
        }
        String unified = original.replace('\\', '/');
        String name = unified.substring(unified.lastIndexOf('/') + 1).trim();
        if (name.isEmpty() || name.contains("..") || name.indexOf('\0') >= 0) {
            return null;
        }
        return name;
    }

    /** 同名不覆盖：在扩展名前加时间戳后缀（保留原名主干，磁盘上仍能认出是哪份卷子） */
    private Path uniqueTarget(Path dir, String fileName) {
        Path target = dir.resolve(fileName);
        if (!Files.exists(target)) {
            return target;
        }
        int dot = fileName.lastIndexOf('.');
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmssSSS"));
        String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";
        return dir.resolve(stem + "-" + stamp + ext);
    }

    // ==================== OCR 通道 ====================

    /** 通道状态由 OcrService 现算：配置改了（补上云端 key）不重启也能在这里看见结果 */
    @Override
    public Map<String, Object> ocrStatus() {
        return ocrService.channels();
    }

    // ==================== 统计 ====================

    @Override
    public CrawlStatsVO stats() {
        CrawlStatsVO vo = new CrawlStatsVO();
        Map<String, Long> stageCounts = new LinkedHashMap<>();
        for (String stage : STAGES) {
            long count = questionMapper.selectCount(
                    Wrappers.<CrawlQuestion>lambdaQuery().eq(CrawlQuestion::getStage, stage));
            stageCounts.put(stage, count);
        }
        vo.setStageCounts(stageCounts);
        vo.setSourceTotal(sourceMapper.selectCount(null));
        vo.setSourceEnabled(sourceMapper.selectCount(
                Wrappers.<CrawlSource>lambdaQuery().eq(CrawlSource::getEnabled, 1)));
        vo.setTaskTotal(taskMapper.selectCount(null));
        vo.setRawTotal(rawMapper.selectCount(null));
        return vo;
    }

    // ==================== 识别与成本概览（S3）====================

    /** 概览默认时间窗：30 天是「这一批文档最近采得怎么样」的常用口径 */
    private static final int SUMMARY_DEFAULT_DAYS = 30;

    /** 上限防手滑：365 天以外的窗口就走「全部」 */
    private static final int SUMMARY_MAX_DAYS = 365;

    /** 待人工关注的文档榜条数 */
    private static final int SUMMARY_WATCHLIST_SIZE = 10;

    @Override
    public CrawlOcrSummaryVO ocrSummary(Integer days) {
        int window = days == null ? SUMMARY_DEFAULT_DAYS
                : Math.min(SUMMARY_MAX_DAYS, Math.max(0, days));
        // 窗口从「今天往前数 window-1 天」的零点起算，保证最后一天是完整自然日
        LocalDateTime since = window > 0
                ? LocalDate.now().minusDays(window - 1L).atStartOfDay() : null;
        CrawlerProperties.Formula formula = properties.getOcr().getFormula();
        int cap = Math.max(1, formula.getMaxCloudPerDocument());

        CrawlOcrSummaryVO vo = new CrawlOcrSummaryVO();
        vo.setDays(window);
        vo.setSince(since);
        vo.setCloudCallCap(cap);
        vo.setEscalateBelow(formula.getEscalateBelow());
        vo.setThresholdNote("本地平均框分低于 " + formula.getEscalateBelow()
                + " 才升级云端；单文档最多 " + cap + " 次云端调用");

        Map<String, Object> totals = lowerKeys(rawMapper.ocrSummaryTotals(since, cap));
        long docTotal = longOf(totals, "doctotal");
        long candidates = longOf(totals, "candidates");
        long recovered = longOf(totals, "recovered");
        long localRecovered = longOf(totals, "localrecovered");
        long cloudRecovered = longOf(totals, "cloudrecovered");
        long cloudCalls = longOf(totals, "cloudcalls");

        vo.setEmpty(docTotal == 0);
        vo.setDocTotal(docTotal);
        vo.setTextLayerDocs(longOf(totals, "textlayerdocs"));
        vo.setScanDocs(longOf(totals, "scandocs"));
        vo.setFormulaDocs(longOf(totals, "formuladocs"));
        vo.setCandidates(candidates);
        vo.setRecovered(recovered);
        vo.setLocalRecovered(localRecovered);
        vo.setCloudRecovered(cloudRecovered);
        vo.setCloudCalls(cloudCalls);
        vo.setUnresolved(Math.max(0, candidates - recovered));
        vo.setCapHitDocs(longOf(totals, "caphitdocs"));
        vo.setRecoveryRate(rate(recovered, candidates));
        vo.setLocalRate(rate(localRecovered, candidates));
        vo.setEscalateRate(rate(cloudCalls, candidates));
        vo.setCloudRate(rate(cloudRecovered, cloudCalls));
        vo.setQualityAvg(doubleOf(totals, "qualityavg"));
        vo.setOcrConfidenceAvg(doubleOf(totals, "ocrconfidenceavg"));

        Map<String, Object> quality = lowerKeys(rawMapper.ocrSummaryQuality(since));
        CrawlOcrSummaryVO.QualityBuckets buckets = new CrawlOcrSummaryVO.QualityBuckets();
        buckets.setHighDocs(longOf(quality, "highdocs"));
        buckets.setMidDocs(longOf(quality, "middocs"));
        buckets.setLowDocs(longOf(quality, "lowdocs"));
        buckets.setUnscoredDocs(longOf(quality, "unscoreddocs"));
        vo.setQuality(buckets);

        vo.setChannels(channelBuckets(since, docTotal));
        vo.setDaily(dailyPoints(rawMapper.ocrSummaryDaily(since)));
        vo.setWatchlist(watchDocs(rawMapper.ocrSummaryWatchlist(since, SUMMARY_WATCHLIST_SIZE), cap));
        return vo;
    }

    /**
     * 通道分布：桶键是 {@code via_ocr|ocr_channels} 拼出来的，标签在这里翻译。
     * 之所以不把文案写进 SQL，是因为「文本层直取但做过片段级重认」这类组合只有代码里说得清，
     * 而且同一段文案还要在解析日志里复用。
     */
    private List<CrawlOcrSummaryVO.ChannelBucket> channelBuckets(LocalDateTime since, long docTotal) {
        List<CrawlOcrSummaryVO.ChannelBucket> list = new ArrayList<>();
        for (Map<String, Object> row : rawMapper.ocrSummaryChannels(since)) {
            Map<String, Object> r = lowerKeys(row);
            String key = String.valueOf(r.getOrDefault("bucket", ""));
            boolean viaText = !key.startsWith("1");
            CrawlOcrSummaryVO.ChannelBucket bucket = new CrawlOcrSummaryVO.ChannelBucket();
            bucket.setKey(key);
            bucket.setLabel(viaText ? "文本层直取" : "扫描件整页 OCR");
            bucket.setSubLabel(channelLabel(key, viaText));
            bucket.setDocs(longOf(r, "docs"));
            bucket.setRatio(docTotal > 0 ? round4(bucket.getDocs() * 1.0 / docTotal) : null);
            list.add(bucket);
        }
        return list;
    }

    /** 通道子标签：空串要分「文本层本来就不需要 OCR」和「老数据没留痕」，两者的处置动作不一样 */
    private String channelLabel(String bucketKey, boolean viaText) {
        int split = bucketKey.indexOf('|');
        String channels = split < 0 ? "" : bucketKey.substring(split + 1);
        if (channels.isBlank()) {
            // 整页 OCR 的文档必然调用过某条通道，通道列为空只可能是 S2 留痕上线之前采集的
            return viaText ? "未走 OCR" : "老数据·通道未留痕";
        }
        boolean local = channels.contains(OcrService.CHANNEL_LOCAL);
        boolean cloud = channels.contains(OcrService.CHANNEL_CLOUD);
        if (local && cloud) {
            return "本地 → 云端";
        }
        return local ? "仅本地" : "仅云端";
    }

    private List<CrawlOcrSummaryVO.DailyPoint> dailyPoints(List<Map<String, Object>> rows) {
        List<CrawlOcrSummaryVO.DailyPoint> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> r = lowerKeys(row);
            CrawlOcrSummaryVO.DailyPoint point = new CrawlOcrSummaryVO.DailyPoint();
            point.setDate(dayLabel(r.get("day")));
            point.setDocs(longOf(r, "docs"));
            point.setCandidates(longOf(r, "candidates"));
            point.setRecovered(longOf(r, "recovered"));
            point.setCloudCalls(longOf(r, "cloudcalls"));
            point.setRecoveryRate(rate(point.getRecovered(), point.getCandidates()));
            list.add(point);
        }
        return list;
    }

    private List<CrawlOcrSummaryVO.WatchDoc> watchDocs(List<CrawlRaw> rows, int cap) {
        List<CrawlOcrSummaryVO.WatchDoc> list = new ArrayList<>();
        for (CrawlRaw raw : rows) {
            CrawlOcrSummaryVO.WatchDoc doc = new CrawlOcrSummaryVO.WatchDoc();
            doc.setId(raw.getId());
            doc.setDocName(fileNameOf(raw.getSourceUrl()));
            doc.setViaOcr(raw.getViaOcr());
            doc.setOcrChannels(raw.getOcrChannels());
            int candidates = raw.getFormulaCandidates() == null ? 0 : raw.getFormulaCandidates();
            int recovered = raw.getFormulaRecovered() == null ? 0 : raw.getFormulaRecovered();
            int calls = raw.getFormulaCloudCalls() == null ? 0 : raw.getFormulaCloudCalls();
            doc.setCandidates(candidates);
            doc.setRecovered(recovered);
            doc.setUnresolved(Math.max(0, candidates - recovered));
            doc.setLocalRecovered(raw.getFormulaLocal());
            doc.setCloudRecovered(raw.getFormulaCloud());
            doc.setCloudCalls(calls);
            // 额度打满的文档，未认出部分是「没继续花钱」而不是「认不出」，榜上必须区分开
            doc.setCapHit(calls >= cap);
            doc.setQualityScore(raw.getQualityScore());
            doc.setParserName(raw.getParserName());
            doc.setParsedCount(raw.getParsedCount());
            doc.setStagedCount(raw.getStagedCount());
            doc.setParseNote(raw.getParseNote());
            doc.setFetchTime(raw.getFetchTime());
            list.add(doc);
        }
        return list;
    }

    /**
     * 聚合结果的列名大小写随方言变化（MySQL 原样返回别名，H2 会转大写），
     * 统一转小写并去掉下划线后按固定键取，省掉两种方言两套写法。
     */
    private Map<String, Object> lowerKeys(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (row != null) {
            row.forEach((k, v) -> out.put(k == null ? "" : k.toLowerCase().replace("_", ""), v));
        }
        return out;
    }

    private long longOf(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private Double doubleOf(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? round4(number.doubleValue()) : null;
    }

    /** 比率保留 4 位；分母为 0 返回 null——「没发现候选」与「发现了没认出」不能都显示成 0% */
    private Double rate(long numerator, long denominator) {
        return denominator <= 0 ? null : round4((double) numerator / denominator);
    }

    private Double round4(double value) {
        return Math.round(value * 10000d) / 10000d;
    }

    /** 日期列在不同驱动下可能是 sql.Date / LocalDate / String，统一收成 yyyy-MM-dd */
    private String dayLabel(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDate date) {
            return date.toString();
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate().toString();
        }
        if (value instanceof java.util.Date date) {
            return java.time.Instant.ofEpochMilli(date.getTime()).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString();
        }
        String text = String.valueOf(value);
        return text.length() > 10 ? text.substring(0, 10) : text;
    }

    // ==================== 内部工具 ====================

    /** 归一并校验来源类型：七期只留 LOCAL_DIR（文档目录），HTTP 已随网页爬虫路线一起删掉 */
    private String normalizeSourceType(String rawType) {
        String type = rawType == null ? "" : rawType.trim().toUpperCase();
        if (!"LOCAL_DIR".equals(type)) {
            throw new BusinessException("七期起采集来源只支持 LOCAL_DIR（文档目录）；"
                    + "网页抓取因公开题库资源几乎不可得、合规风险高而移除");
        }
        return type;
    }

    private Map<Long, String> subjectNames() {
        return subjectMapper.selectList(null).stream()
                .collect(Collectors.toMap(Subject::getId, Subject::getSubjectName));
    }

    private Map<Long, String> sourceNames() {
        return sourceMapper.selectList(null).stream()
                .collect(Collectors.toMap(CrawlSource::getId, CrawlSource::getName));
    }

    private CrawlSourceVO toSourceVO(CrawlSource source, Map<Long, String> subjectNames) {
        CrawlSourceVO vo = new CrawlSourceVO();
        vo.setId(source.getId());
        vo.setName(source.getName());
        vo.setBaseUrl(source.getBaseUrl());
        vo.setSourceType(source.getSourceType());
        vo.setLicenseNote(source.getLicenseNote());
        vo.setRateLimitMs(source.getRateLimitMs());
        vo.setDefaultSubjectId(source.getDefaultSubjectId());
        vo.setDefaultSubjectName(subjectNames.get(source.getDefaultSubjectId()));
        vo.setEnabled(source.getEnabled());
        vo.setRemark(source.getRemark());
        vo.setCreateTime(source.getCreateTime());
        return vo;
    }

    /**
     * 原文行 → 解析日志行。分页查询没 select content，这里不会触发大字段加载；
     * 详情走 selectById 后再单独补预览字段。
     */
    private CrawlDocumentVO toDocumentVO(CrawlRaw raw) {
        CrawlDocumentVO vo = new CrawlDocumentVO();
        vo.setId(raw.getId());
        vo.setTaskId(raw.getTaskId());
        vo.setContentHash(raw.getContentHash());vo.setParserVersion(raw.getParserVersion());vo.setPageCount(raw.getPageCount());
        vo.setSourceUrl(raw.getSourceUrl());
        vo.setDocName(raw.getSourceName()==null?fileNameOf(raw.getSourceUrl()):raw.getSourceName());
        vo.setViaOcr(raw.getViaOcr());
        vo.setCharCount(raw.getCharCount());
        vo.setParserName(raw.getParserName());
        vo.setParsedCount(raw.getParsedCount());
        vo.setStagedCount(raw.getStagedCount());
        vo.setParseNote(raw.getParseNote());
        vo.setQualityScore(raw.getQualityScore());
        vo.setQualityDetail(raw.getQualityDetail());
        vo.setOcrConfidence(raw.getOcrConfidence());
        vo.setOcrChannels(raw.getOcrChannels());
        vo.setFormulaCandidates(raw.getFormulaCandidates());
        vo.setFormulaRecovered(raw.getFormulaRecovered());
        vo.setFormulaLocal(raw.getFormulaLocal());
        vo.setFormulaCloud(raw.getFormulaCloud());
        vo.setFormulaCloudCalls(raw.getFormulaCloudCalls());
        vo.setFetchTime(raw.getFetchTime());
        return vo;
    }

    /** 从落库的来源标识里取出文件名：本地路径带反斜杠，上传目录带正斜杠，两种都要认 */
    private String fileNameOf(String sourceUrl) {
        if (!StringUtils.hasText(sourceUrl)) {
            return "";
        }
        String path = sourceUrl.replace('\\', '/');
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    private CrawlQuestionVO toQuestionVO(CrawlQuestion question, Map<Long, String> subjectNames) {
        CrawlQuestionVO vo = new CrawlQuestionVO();
        vo.setId(question.getId());
        vo.setRawId(question.getRawId());
        vo.setSourcePage(question.getSourcePage());vo.setSourceAnchor(question.getSourceAnchor());
        vo.setSourceBbox(question.getSourceBbox());vo.setLocatorMethod(question.getLocatorMethod());vo.setReviewMilliseconds(question.getReviewMilliseconds());
        vo.setRevision(question.getRevision() == null ? 1 : question.getRevision());
        vo.setFormulaConfirmed(question.getFormulaConfirmed());
        vo.setStage(question.getStage());
        vo.setGrade(question.getGrade());
        vo.setGradeLevel(question.getGradeLevel());
        vo.setSubjectId(question.getSubjectId());
        vo.setSubjectName(subjectNames.get(question.getSubjectId()));
        vo.setQuestionType(question.getQuestionType());
        vo.setTypeName(QuestionTypeEnum.labelOf(question.getQuestionType()));
        vo.setContent(question.getContent());
        vo.setOptions(JsonHelper.toOptionMap(question.getOptions()));
        vo.setAnswer(question.getAnswer());
        vo.setAnalysis(question.getAnalysis());
        vo.setDifficulty(question.getDifficulty());
        vo.setKnowledgeTags(question.getKnowledgeTags());
        vo.setQualityFlags(question.getQualityFlags());
        vo.setReviewNote(question.getReviewNote());
        vo.setPromotedQuestionId(question.getPromotedQuestionId());
        vo.setCleanTime(question.getCleanTime());
        vo.setCreateTime(question.getCreateTime());
        return vo;
    }
}
