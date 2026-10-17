package com.zhikao.modules.crawler.fetch;

import com.zhikao.common.BusinessException;
import com.zhikao.config.CrawlerProperties;
import com.zhikao.entity.CrawlSource;
import com.zhikao.modules.crawler.extract.ExtractedText;
import com.zhikao.modules.crawler.extract.ExtractorRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * 文档采集器（七期）：遍历 LOCAL_DIR 来源目录，把每个文档抽成文本交给流水线。
 *
 * <p><b>七期把「上网爬网页」这条路线整个删掉了</b>，原因很实际：公开、可自由抓取、
 * 且有版权授权的题库站点几乎不存在，robots 一挡就只剩不下题的入口页；
 * 而真实的题源本来就是<b>文档</b>——老师手里的 docx/pdf、手机拍的试卷图片。
 * 于是采集层的职责收敛成一件事：<b>把文件变成文字</b>。
 * 变的只是取字的方式（文本层直读 / PDF 渲染后 OCR / 图片 OCR），
 * 下游「解析→清洗→打标→待审→入库」整条链路一行没动。
 *
 * <p>两条底线保留：
 * <ol>
 *   <li>只读白名单目录（{@link LocalPathGuard}）——包括配置根与上传摄取目录，
 *       不给「传个路径就读服务器」留口子；</li>
 *   <li>单文件体积上限（{@code max-doc-bytes}）——超限直接跳过，不把一次采集变成磁盘与内存的事故。</li>
 * </ol>
 *
 * <p>单个文件抽取失败只跳过该文件（记日志），其它文件照常——
 * 一本坏 PDF 不该毁掉整个目录里另外四十份文档。
 */
@Component
public class DocumentFetcher {

    private static final Logger log = LoggerFactory.getLogger(DocumentFetcher.class);

    private final CrawlerProperties properties;
    private final LocalPathGuard pathGuard;
    private final ExtractorRegistry extractors;
    private final com.zhikao.modules.crawler.DocumentVersions versions;

    public DocumentFetcher(CrawlerProperties properties, LocalPathGuard pathGuard,
                           ExtractorRegistry extractors, com.zhikao.modules.crawler.DocumentVersions versions) {
        this.versions=versions;
        this.properties = properties;
        this.pathGuard = pathGuard;
        this.extractors = extractors;
    }

    /**
     * 采集一个来源目录下的全部可识别文档。
     *
     * @param alreadyIngested 该文件的定位是否已采过（按绝对路径判定）。
     *                        <b>必须先问再抽</b>：抽取（尤其 OCR）是十秒级的，
     *                        如果等抽完文本来回再按 dedup_hash 跳过，每次重跑都要把整个目录重新 OCR 一遍。
     * @throws BusinessException 来源类型不对 / 目录不合法 / 目录下没有任何可识别文档
     */
    public List<FetchedItem> fetch(CrawlSource source, Predicate<String> alreadyIngested) {
        if (!"LOCAL_DIR".equalsIgnoreCase(source.getSourceType())) {
            throw new BusinessException("采集只支持本地目录来源（LOCAL_DIR），当前类型：" + source.getSourceType());
        }
        Path dir = pathGuard.requireAllowed(source.getBaseUrl());
        if (!Files.isDirectory(dir)) {
            throw new BusinessException("采集来源不是目录：" + dir);
        }
        List<Path> files = listDocuments(dir);
        if (files.isEmpty()) {
            throw new BusinessException("目录下没有可识别的文档（支持 docx/doc/pdf/图片/md/txt/json/jsonl/csv）：" + dir);
        }
        List<FetchedItem> items = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (Path file : files) {
            String location = file.toAbsolutePath().normalize().toString();
            if (alreadyIngested.test(location)) {
                continue;
            }
            Path snapshot=versions.capture(file);
            long started=System.nanoTime();
            ExtractedText extracted = extractOne(snapshot, failures);
            long elapsed=java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started);
            if (extracted == null || extracted.text() == null || extracted.text().isBlank()) {
                continue;
            }
            items.add(new FetchedItem(location, extracted.text(), extracted.via(), extracted.trace(),snapshot.toString(),versions.contentHash(snapshot),elapsed));
        }
        // 一个文件都没取出文字，且有失败记录：这通常是「OCR 通道没配」这类配置问题，必须显式失败，
        // 绝不能静默返回空列表——那会让使用者以为「文档里没有题」，白折腾一轮。
        if (items.isEmpty() && !failures.isEmpty()) {
            throw new BusinessException("所有文档都未能取出文字：" + failures.get(0));
        }
        return items;
    }

    /** 递归列出目录下受支持的文档文件，跳过超限的超大文件 */
    private List<Path> listDocuments(Path dir) {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(p -> !p.toString().contains(".versions"))
                    .filter(Files::isRegularFile)
                    .filter(p -> extractors.supports(p.getFileName().toString()))
                    .sorted()
                    .forEach(p -> {
                        long size = sizeOf(p);
                        if (size > properties.getMaxDocBytes()) {
                            log.warn("文档超过体积上限（{}B > {}B），跳过：{}",
                                    size, properties.getMaxDocBytes(), p);
                            return;
                        }
                        files.add(p);
                    });
        } catch (IOException e) {
            throw new BusinessException("遍历目录失败：" + e.getMessage());
        }
        return files;
    }

    /** 抽取单个文件：失败记一次原因并返回 null，不影响同目录其它文件 */
    private ExtractedText extractOne(Path file, List<String> failures) {
        try {
            var extractor = extractors.select(file.getFileName().toString());
            if (extractor == null) {
                return null;
            }
            return extractor.extract(file);
        } catch (Exception e) {
            failures.add(e.getMessage());
            log.warn("文档抽取失败，跳过：{}（{}）", file, e.getMessage());
            return null;
        }
    }

    private long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0L;
        }
    }
}
