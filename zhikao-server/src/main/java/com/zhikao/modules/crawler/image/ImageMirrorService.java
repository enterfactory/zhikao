package com.zhikao.modules.crawler.image;

import com.zhikao.common.LoginUtil;
import com.zhikao.config.CrawlerProperties;
import com.zhikao.entity.CrawlSource;
import com.zhikao.entity.SysFile;
import com.zhikao.modules.crawler.clean.CleanResult;
import com.zhikao.modules.crawler.fetch.LocalPathGuard;
import com.zhikao.modules.file.FileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 图片镜像服务（六期任务#19）：把题干/解析里引用的外链图片搬进本地文件库，改写成本系统可访问的 src。
 *
 * <p><b>为什么要镜像</b>：直接留外部 URL 有两条死路——对方站点防盗链或删图，题目就变成一片空白；
 * 而且「图片存在第三方服务器上」意味着学生的答题页每次渲染都在向陌生站点发请求。
 * 镜像到 {@code sys_file} 后，题目内容与它的配图是同生共死的。
 *
 * <p><b>下载底线</b>（七期删掉了网页抓取，但「文档里引用的外链图片」依然存在，
 * 因此这套约束保留，不因为「只是张图」就放宽）：
 * <ol>
 *   <li>UA 固定 {@code ZhikaoBot/1.0 (exam-bank research)}，绝不伪装浏览器；</li>
 *   <li>只走 http/https（外加本地白名单目录里的相对路径/file 路径），其他协议一律不碰；</li>
 *   <li>超时取 {@code zhikao.crawler.image-timeout-ms}，单张体积上限 {@code image-max-bytes}（默认 5MB），
 *       读流时边读边判，超限直接断开，不会把超大响应整块吸进堆；</li>
 *   <li>响应必须真的像图片（文件头魔数或 SVG 文本特征），否则按失败处理——
 *       「200 但回了个 HTML 错误页」是最常见的假成功。</li>
 * </ol>
 *
 * <p><b>本地图片</b>：采集目录里的 {@code img src} 常是相对该文档的路径，
 * 这里以「源文件所在目录」为基准解析，再过 {@link LocalPathGuard} 白名单闸门，
 * 因此 {@code ../../../Windows/...} 这类构造读到的是拒绝而不是越权。
 *
 * <p><b>失败即降级</b>：任何一张图没镜像成功，保留原 src 并给这条题追加 {@code IMG_MIRROR_FAILED} 质量标记，
 * 让人审看得见——绝不因为一张图挂了就把整道题丢掉。
 */
@Service
public class ImageMirrorService {

    private static final Logger log = LoggerFactory.getLogger(ImageMirrorService.class);

    /** 文件库归属标记：异步线程可能没有登录态，兜底用这个身份 */
    private static final String DEFAULT_OWNER = "crawler";

    /** 镜像文件的业务标签，便于事后按业务反查附件 */
    private static final String BIZ_TYPE = "crawl-image";

    /** img 标签（非贪婪，属性里不含 >） */
    private static final Pattern IMG_TAG = Pattern.compile("(?is)<img\\b[^>]*>");
    private static final Pattern SRC_ATTR = Pattern.compile("(?is)\\bsrc\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    private static final Pattern ALT_ATTR = Pattern.compile("(?is)\\balt\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    private static final Pattern WIDTH_ATTR = Pattern.compile("(?is)\\bwidth\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    /** SVG 里的可执行内容：脚本元素、内联事件、CDATA 包裹的脚本 */
    private static final Pattern SVG_SCRIPT = Pattern.compile("(?is)<\\s*script\\b.*?<\\s*/\\s*script\\s*>|<!\\[CDATA\\[.*?]]>");
    private static final Pattern SVG_EVENT_ATTR = Pattern.compile("(?is)\\s+on[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");

    /** 已经指向本系统文件库的 src 前缀：再次流水线时直接跳过，保证幂等 */
    private static final String ALREADY_MIRRORED = "/api/file/";

    private final CrawlerProperties properties;
    private final FileStorageService fileStorageService;
    private final LocalPathGuard pathGuard;
    private final ImageTargetPolicy targets;
    private final HttpClient httpClient;

    /** 同一张图的镜像结果缓存（key = 基准位置 + 原 src）：LRU 限长，避免长跑任务把内存吃满 */
    private final Map<String, String> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 256;
        }
    };

    public ImageMirrorService(CrawlerProperties properties, FileStorageService fileStorageService,
                              LocalPathGuard pathGuard,ImageTargetPolicy targets) {
        this.targets=targets;
        this.properties = properties;
        this.fileStorageService = fileStorageService;
        this.pathGuard = pathGuard;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getImageTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * 对一条清洗产物做图片镜像：就地改写 content / analysis / answer / options 里的 img src。
     *
     * @param clean          清洗结果（原地修改）
     * @param source         来源配置（决定相对路径基准是本地目录还是网页 URL）
     * @param originLocation 该条原文的定位：采集目录下的文件绝对路径
     * @return 是否至少有一张图镜像失败（调用方据此追加 IMG_MIRROR_FAILED 标记）
     */
    public boolean mirror(CleanResult clean, CrawlSource source, String originLocation) {
        if (clean == null) {
            return false;
        }
        Context context = new Context(source, originLocation);
        clean.setContent(rewrite(clean.getContent(), context));
        clean.setAnalysis(rewrite(clean.getAnalysis(), context));
        clean.setAnswer(rewrite(clean.getAnswer(), context));

        Map<String, String> options = clean.getOptions();
        if (options != null && !options.isEmpty()) {
            Map<String, String> rewritten = new LinkedHashMap<>(options.size());
            for (Map.Entry<String, String> entry : options.entrySet()) {
                rewritten.put(entry.getKey(), rewrite(entry.getValue(), context));
            }
            clean.setOptions(rewritten);
        }
        return context.failed;
    }

    /** 改写一段文本里的所有 img src；单张失败只跳过该张，其余照常 */
    private String rewrite(String text, Context context) {
        if (!StringUtils.hasText(text) || text.toLowerCase().indexOf("<img") < 0) {
            return text;
        }
        Matcher matcher = IMG_TAG.matcher(text);
        StringBuilder out = new StringBuilder(text.length());
        while (matcher.find()) {
            String tag = matcher.group();
            String src = attribute(SRC_ATTR, tag);
            String replacement = tag;
            if (StringUtils.hasText(src) && !isSkipped(src)) {
                String localUrl = mirrorOne(src, context);
                if (localUrl != null) {
                    replacement = buildImgTag(localUrl, attribute(ALT_ATTR, tag), attribute(WIDTH_ATTR, tag));
                } else {
                    // 这张图搬不进库：保留原标签（前端或许还能显示），并记一次失败供上层打标记
                    context.failed = true;
                    log.debug("图片未能镜像，保留原地址：{}", src);
                }
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** 无需镜像的 src：已在本地文件库（重复跑流水线时幂等）或已是内联数据 */
    private boolean isSkipped(String src) {
        String raw = src.trim();
        return raw.startsWith(ALREADY_MIRRORED) || raw.toLowerCase().startsWith("data:");
    }

    /**
     * 镜像一张图：成功返回系统内可访问地址（{@code /api/file/view/{id}}），失败返回 null（调用方保留原 src）。
     */
    private String mirrorOne(String src, Context context) {
        String raw = src.trim();
        if (isSkipped(raw)) {
            return null; // 已在库内 / 内联数据：不做二次搬运（幂等）
        }
        String key = context.origin + "|" + raw;
        synchronized (cache) {
            if (cache.containsKey(key)) {
                return cache.get(key);
            }
        }
        String url = null;
        try {
            ImagePayload payload = null;
            if (isHttp(raw)) {
                payload = download(raw);
            } else if (isLocalPath(raw, context)) {
                payload = readLocal(resolveLocal(raw, context));
            } else {
                log.debug("图片地址既不是 http 也不在白名单目录内，保留原地址：{}", raw);
            }
            if (payload != null) {
                SysFile file = fileStorageService.storeImage(payload.bytes(),
                        ensureExtension(displayName(raw), payload.ext()),
                        null, BIZ_TYPE, owner());
                url = fileStorageService.urlOf(file);
            }
        } catch (Exception e) {
            log.warn("题目配图镜像失败，保留原地址：{}（{}）", raw, e.getMessage());
        }
        synchronized (cache) {
            cache.put(key, url);
        }
        return url;
    }

    /** 展示用文件名：去掉查询串与路径，只留最后一段（真正的磁盘名仍由文件库用 UUID 生成） */
    private String displayName(String raw) {
        String path = raw.replaceAll("[?#].*$", "");
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return StringUtils.hasText(name) ? name : "image.png";
    }


    /** HTTP 下载：固定 UA + 超时 + 体积上限（边读边判，超限即断） */
    private ImagePayload download(String url) throws Exception {
        URI uri = URI.create(url);
        targets.requireAllowed(uri);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            log.debug("非 http/https 协议，拒绝镜像：{}", url);
            return null;
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(properties.getImageTimeoutMs()))
                .header("User-Agent", properties.getUserAgent())
                .GET()
                .build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = response.body()) {
            if (response.statusCode() / 100 != 2) {
                log.debug("图片响应非 2xx，跳过：{} -> {}", url, response.statusCode());
                return null;
            }
            byte[] bytes = readLimited(in, properties.getImageMaxBytes());
            if (bytes == null) {
                log.debug("图片超过体积上限（{}B），跳过：{}", properties.getImageMaxBytes(), url);
                return null;
            }
            String ext = detectImageExtension(bytes);
            if (ext == null) {
                log.debug("响应内容不像图片（可能是错误页），跳过：{}", url);
                return null;
            }
            return new ImagePayload(prepare(bytes, ext), ext);
        }
    }

    /** 读流但不超过上限：返回 null 表示超限 */
    private byte[] readLimited(InputStream in, int maxBytes) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(Math.min(maxBytes, 64 * 1024));
        byte[] chunk = new byte[8192];
        int total = 0;
        int read;
        while ((read = in.read(chunk)) != -1) {
            total += read;
            if (total > maxBytes) {
                return null;
            }
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    /** 本地文件读取：路径已由调用方过 {@link LocalPathGuard} 白名单闸门 */
    private ImagePayload readLocal(Path path) throws Exception {
        if (path == null || !Files.isRegularFile(path)) {
            log.debug("本地图片不存在，跳过：{}", path);
            return null;
        }
        long size = Files.size(path);
        if (size <= 0 || size > properties.getImageMaxBytes()) {
            log.debug("本地图片大小不合法（{}B），跳过：{}", size, path);
            return null;
        }
        byte[] bytes = Files.readAllBytes(path);
        String ext = extensionOf(path.getFileName().toString());
        if (ext == null) {
            ext = detectImageExtension(bytes);
        }
        if (ext == null) {
            log.debug("本地文件不是可识别图片，跳过：{}", path);
            return null;
        }
        return new ImagePayload(prepare(bytes, ext), ext);
    }

    /** SVG 入库前的最小净化：剥掉 script 元素、CDATA 段与内联事件属性（位图无需处理） */
    private byte[] prepare(byte[] bytes, String ext) {
        if (!"svg".equals(ext)) {
            return bytes;
        }
        String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        String sanitized = SVG_EVENT_ATTR.matcher(SVG_SCRIPT.matcher(text).replaceAll(" ")).replaceAll("");
        return sanitized.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** 按文件头魔数判定图片类型；不是图片返回 null（宁可不镜像也不入库垃圾） */
    private String detectImageExtension(byte[] bytes) {
        if (bytes == null || bytes.length < 8) {
            return null;
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "png";
        }
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8') {
            return "gif";
        }
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes.length > 11 && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "webp";
        }
        if (bytes[0] == 'B' && bytes[1] == 'M') {
            return "bmp";
        }
        // SVG 是文本：跳过空白与 XML 声明后应当以 <svg / <!DOCTYPE svg 起头
        String head = new String(bytes, 0, Math.min(bytes.length, 256), java.nio.charset.StandardCharsets.UTF_8)
                .trim();
        if (head.startsWith("<?xml")) {
            int end = head.indexOf("?>");
            head = end >= 0 ? head.substring(end + 2).trim() : head;
        }
        return head.startsWith("<svg") || head.startsWith("<!DOCTYPE svg") || head.startsWith("<!doctype svg")
                ? "svg" : null;
    }

    /** 解析本地图片路径：file: 协议直接取路径；相对路径以「源文件所在目录」为基准，再过白名单 */
    private Path resolveLocal(String src, Context context) {
        String cleaned = src.replaceAll("[?#].*$", "");
        try {
            if (cleaned.toLowerCase().startsWith("file:")) {
                return Paths.get(URI.create(cleaned)).toAbsolutePath().normalize();
            }
            Path originDir = context.localDir();
            if (originDir == null) {
                return null;
            }
            Path candidate = cleaned.startsWith("/") ? originDir.resolve(cleaned.substring(1))
                    : originDir.resolve(cleaned);
            return candidate.normalize();
        } catch (Exception e) {
            log.debug("本地图片路径解析失败：{}（{}）", src, e.getMessage());
            return null;
        }
    }

    /** 本地路径必须落在爬虫白名单根之内，越界即视为不可读 */
    private boolean isLocalPath(String src, Context context) {
        String lower = src.toLowerCase();
        if (lower.startsWith("file:")) {
            return withinAllowedRoots(resolveLocal(src, context));
        }
        if (isHttp(src) || src.startsWith("//") || context.localDir() == null) {
            return false;
        }
        return withinAllowedRoots(resolveLocal(src, context));
    }

    private boolean isHttp(String src) {
        String lower = src.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /** 本地路径必须落在爬虫白名单根之内，越界即视为不可读（挡住 ../ 逃逸与相似前缀） */
    private boolean withinAllowedRoots(Path path) {
        if (path == null) {
            return false;
        }
        for (Path root : pathGuard.allowedRoots()) {
            if (path.startsWith(root)) {
                return true;
            }
        }
        return false;
    }

    /** 展示名补齐真实扩展名：URL 常没有扩展名（如 …/w3c_main），而文件库按扩展名做落盘与回放 */
    private String ensureExtension(String name, String ext) {
        return extensionOf(name) == null ? name + "." + ext : name;
    }

    /** 重建 img 标签：只带 src 与原有 alt/width，其他属性一律不带回（净化白名单同规则） */
    private String buildImgTag(String src, String alt, String width) {
        StringBuilder tag = new StringBuilder("<img src=\"").append(src).append("\"");
        if (StringUtils.hasText(alt)) {
            tag.append(" alt=\"").append(alt.replace("\"", "&quot;")).append("\"");
        }
        if (StringUtils.hasText(width)) {
            tag.append(" width=\"").append(width.replace("\"", "&quot;")).append("\"");
        }
        return tag.append(">").toString();
    }

    /** 取属性值（三张写法：双引号 / 单引号 / 裸值） */
    private String attribute(Pattern pattern, String tag) {
        Matcher matcher = pattern.matcher(tag);
        if (!matcher.find()) {
            return null;
        }
        String value = matcher.group(2);
        if (value == null) {
            value = matcher.group(3);
        }
        return value == null ? matcher.group(4) : value;
    }

    private String extensionOf(String name) {
        String cleanName = name.replaceAll("[?#].*$", "").toLowerCase();
        int dot = cleanName.lastIndexOf('.');
        if (dot < 0 || dot == cleanName.length() - 1) {
            return null;
        }
        String ext = cleanName.substring(dot + 1);
        return switch (ext) {
            case "jpg", "jpeg", "png", "gif", "webp", "bmp" -> ext.equals("jpeg") ? "jpg" : ext;
            case "svg" -> "svg";
            default -> null;
        };
    }

    /** 归属人：异步线程上取不到实时登录态时回退到 crawler，避免文件记成「无归属」 */
    private String owner() {
        try {
            String username = LoginUtil.username();
            return StringUtils.hasText(username) ? username : DEFAULT_OWNER;
        } catch (Exception e) {
            return DEFAULT_OWNER;
        }
    }

    /**
     * 下载/读取到的图片本体与判定出的真实扩展名。
     */
    private record ImagePayload(byte[] bytes, String ext) {
    }

    /**
     * 单条原文的镜像上下文：预先把「本地目录 / 页面 URL」两种基准算好，避免每张图重复解析；
     * 同时充当本次调用的失败位（有任何一张图没搬进来，上层就给这道题追加 IMG_MIRROR_FAILED）。
     */
    private static final class Context {

        private final CrawlSource source;
        private final String origin;
        /** 本次镜像是否有图失败（可变，所以这里不是 record） */
        private boolean failed;

        private Context(CrawlSource source, String origin) {
            this.source = source;
            this.origin = origin;
        }

        /** 采集源里的相对图片以此目录为基准（源是文件则取其父目录） */
        Path localDir() {
            if (origin == null || source == null || !"LOCAL_DIR".equalsIgnoreCase(source.getSourceType())) {
                return null;
            }
            try {
                Path path = Paths.get(origin).toAbsolutePath().normalize();
                return Files.isDirectory(path) ? path : path.getParent();
            } catch (Exception e) {
                return null;
            }
        }
    }
}
