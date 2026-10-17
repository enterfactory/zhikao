package com.zhikao.modules.crawler.fetch;

import com.zhikao.common.BusinessException;
import com.zhikao.config.CrawlerProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 本地目录白名单守卫：LOCAL_DIR 采集、文档上传、本地配图镜像的越权硬闸门。
 *
 * <p>合规要点落点之一。规则：
 * <ol>
 *   <li>入参必须是<b>绝对路径</b>（相对路径一律拒绝，避免以不可预测的工作目录为基准）；</li>
 *   <li>规范化（消除 {@code ..}、解析符号链接前先做词法 normalize）后，
 *       必须落在某个白名单根<b>之内或就是根本身</b>；</li>
 *   <li>白名单根 = 配置 {@code zhikao.crawler.allowed-roots} + 上传摄取目录 {@code ingest-dir}，
 *       相对根以 {@code user.dir} 为基准解析。</li>
 * </ol>
 * 用 {@link Path#startsWith(Path)} 判定包含，天然挡住「/allowed-root-evil」这类前缀字符串伪装。
 *
 * <p>上传摄取目录在这里<b>建目录而不是假设它存在</b>：写盘前必须确认它真的落成一个目录，
 * 否则同名文件占位或权限不足会让上传得到一个看不懂的 IO 异常。
 */
@Component
public class LocalPathGuard {

    private final CrawlerProperties properties;

    public LocalPathGuard(CrawlerProperties properties) {
        this.properties = properties;
    }

    /** 校验目录路径合法：不合法直接抛业务异常，合法返回规范化后的绝对路径 */
    public Path requireAllowed(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new BusinessException("目录路径不能为空");
        }
        Path candidate;
        try {
            candidate = Paths.get(rawPath).normalize();
        } catch (Exception e) {
            throw new BusinessException("目录路径格式不合法：" + rawPath);
        }
        if (!candidate.isAbsolute()) {
            throw new BusinessException("目录路径必须是绝对路径");
        }
        for (Path root : allowedRoots()) {
            // startsWith 是「路径段」级比较，不是字符串前缀，天然防 ../ 逃逸与相似前缀
            if (candidate.startsWith(root)) {
                try {
                    if (Files.exists(candidate) && !candidate.toRealPath().startsWith(root.toRealPath())) continue;
                } catch (IOException e) { throw new BusinessException("路径无法访问"); }
                return candidate;
            }
        }
        throw new BusinessException("目录不在允许访问的白名单根之下，拒绝读取：" + candidate
                + "（可在 zhikao.crawler.allowed-roots 中追加根目录）");
    }

    /**
     * 上传摄取目录的绝对路径，并确保它已存在（不存在则创建）。
     * 上传的 docx/pdf/图片都落在这里，再由 LOCAL_DIR 采集链路取字——
     * 「上传」和「本地目录」因此是同一条流水线，不需要两套采集实现。
     */
    public Path ensureIngestRoot() {
        Path baseDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path configured = Paths.get(properties.getIngestDir());
        Path root = (configured.isAbsolute() ? configured : baseDir.resolve(configured)).normalize();
        try {
            if (!Files.isDirectory(root)) {
                Files.createDirectories(root);
            }
        } catch (IOException e) {
            throw new BusinessException("采集摄取目录无法创建：" + root + "（" + e.getMessage() + "）");
        }
        return root;
    }

    /** 解析全部白名单根为规范化绝对路径（含上传摄取目录） */
    public List<Path> allowedRoots() {
        Set<Path> roots = new LinkedHashSet<>();
        Path baseDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (String configured : properties.getAllowedRoots()) {
            if (configured == null || configured.isBlank()) {
                continue;
            }
            Path resolved = Paths.get(configured);
            if (!resolved.isAbsolute()) {
                resolved = baseDir.resolve(resolved);
            }
            roots.add(resolved.normalize());
        }
        // 上传目录自动在白名单内：否则「网页上传→落盘→再采集」这条路每一步都要手工加配置
        roots.add(ensureIngestRoot());
        return new ArrayList<>(roots);
    }
}
