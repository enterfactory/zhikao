package com.zhikao.modules.crawler.extract.formula;

import com.zhikao.config.CrawlerProperties;
import com.zhikao.modules.crawler.ocr.OcrService;
import com.zhikao.modules.crawler.ocr.OcrText;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 内联公式片段回插（文本层为底，只重认丢字的那一小块）。
 *
 * <p><b>要解决的问题</b>：试卷 PDF 里的数学公式绝大多数是<b>内联图片对象</b>，
 * 不是文本层字符。于是取字取到的是「已知函数 的最小值为」——正文一行没丢，
 * 条件里的式子凭空消失。这类文档平均每页字符数是达标的（正文本来就是真文本层），
 * 走「整页重 OCR」会把 480 个精确字符换成 80% 置信度的概率结果，<b>严格更差</b>。
 *
 * <p><b>做法</b>：
 * <ol>
 *   <li>图形层遍历（{@link PDFGraphicsStreamEngine}）拿到页内每个图片对象的<b>放置矩形</b>
 *       ——按放置尺寸而不是像素尺寸筛：行内公式一般是 10~40pt 高的小图，题图/插图则是 100pt 级；</li>
 *   <li>文本层遍历（{@link PDFTextStripper}）拿到每次写入的<b>起点坐标与在输出串里的偏移</b>，
 *       于是「这一行、这个式子右边」能映射成一个可插入的位置；</li>
 *   <li>逐个片段裁出来放大后<b>级联识别</b>：本地先认（免费），置信度不够再升级云端（要 LaTeX，且受成本闸约束）；</li>
 *   <li>结果按 {@code $...$} 插回原偏移（同页多处按偏移<b>倒序</b>插入，前面的插入不会让后面的偏移失效）。</li>
 * </ol>
 *
 * <p>刻意保守：文本层一个字都不改，认不出来就留原样（宁可缺，不可错插）；
 * 页面有旋转、图片带旋转/错切时坐标不可比，直接跳过；识别结果像一句中文解释（而不是式子）也丢弃。
 * 因此<b>没有任何片段的文档，输出与不走这段代码完全一致</b>。
 */
@Component
public class InlineFormulaResolver {

    private static final Logger log = LoggerFactory.getLogger(InlineFormulaResolver.class);

    /** 片段送 OCR 前想要的最小像素高：二三十像素的式子识别模型基本认不动 */
    private static final int MIN_OCR_HEIGHT = 64;

    private static final int MAX_UPSCALE = 8;

    /** 片段放大后的像素上限：超过就把倍数降下来，公式识别不需要亿级像素 */
    private static final long MAX_FRAGMENT_PIXELS = 12_000_000L;

    /** 放大后四周补的白边（像素）：OCR 检测层需要边界留白 */
    private static final int PADDING = 16;

    private final CrawlerProperties properties;
    private final OcrService ocrService;

    public InlineFormulaResolver(CrawlerProperties properties, OcrService ocrService) {
        this.properties = properties;
        this.ocrService = ocrService;
    }

    /**
     * @param text       插回公式后的正文（与原文本层逐字一致，只在中间多出 {@code $...$}）
     * @param candidates 判定为公式片段的图片数
     * @param recovered  成功插回的片段数（= local + cloud）
     * @param local      其中本地通道认出的
     * @param cloud      其中升级云端后认出的
     * @param localCalls 真正发给本地通道认过的次数（每层识别率的分母）
     * @param cloudCalls 真正发给云端认过的次数（= 花钱次数，与 cloud 的差就是白花的）
     * @param missed     认不出、保持原样的
     * @param unplaced   找不到可插入位置、保持原样的
     * @param note       给人看的一句话结论（进「解析日志」的质量构成）
     */
    public record Outcome(String text, int candidates, int recovered, int local, int cloud,
                          int localCalls, int cloudCalls, int missed, int unplaced, String note) {

        /** 本层实际用到的 OCR 通道，按调用先后逗号分隔（落 {@code crawl_raw.ocr_channels}） */
        public String channels() {
            if (localCalls > 0 && cloudCalls > 0) {
                return OcrService.CHANNEL_LOCAL + "," + OcrService.CHANNEL_CLOUD;
            }
            if (localCalls > 0) {
                return OcrService.CHANNEL_LOCAL;
            }
            return cloudCalls > 0 ? OcrService.CHANNEL_CLOUD : null;
        }
    }

    /**
     * 逐页把内联小图当公式候选重认并插回。
     *
     * @param pages 处理的页数（调用方已按 max-pages 截断，页号从 1 开始）
     * @return 没有可用配置或未找到任何候选时返回 null（调用方保持原样）
     */
    public Outcome resolve(PDDocument document, int pages) throws IOException {
        CrawlerProperties.Formula config = properties.getOcr().getFormula();
        if (!config.isEnabled() || pages <= 0) {
            return null;
        }
        Stats stats = new Stats();
        int fragmentLimit = Math.max(0, config.getMaxPerDocument());
        StringBuilder text = new StringBuilder();

        for (int page = 1; page <= pages; page++) {
            PDPage pdPage = document.getPages().get(page - 1);
            List<Placement> candidates = isRotated(pdPage)
                    ? List.of()
                    : collectCandidates(pdPage, page, config, stats, fragmentLimit - stats.candidates);
            if (candidates.isEmpty()) {
                text.append(readPlainText(document, page)).append('\n');
                continue;
            }
            text.append(resolvePage(document, page, candidates, config, stats));
            text.append('\n');
        }
        if (stats.candidates == 0) {
            return null;
        }
        String note = buildNote(stats);
        log.info("公式片段回插：候选 {} · 插回 {}（本地 {} · 云端 {}） · 调用 本地 {} · 云端 {} · 未认出 {} · 无定位 {}",
                stats.candidates, stats.recovered, stats.local, stats.cloud,
                stats.localCalls, stats.cloudUsed, stats.missed, stats.unplaced);
        return new Outcome(text.toString(), stats.candidates, stats.recovered, stats.local, stats.cloud,
                stats.localCalls, stats.cloudUsed, stats.missed, stats.unplaced, note);
    }

    /** 一页的处理：定位每个片段的插入偏移 → 按原文顺序识别 → 按偏移倒序插回 */
    private String resolvePage(PDDocument document, int pageNumber, List<Placement> candidates,
                               CrawlerProperties.Formula config, Stats stats) throws IOException {
        AnchoredStripper stripper = new AnchoredStripper();
        String pageText = stripper.read(document, pageNumber);
        List<Anchor> anchors = stripper.anchors;

        // 按插入偏移分组：同一点要插多个片段时（一行里连着两个式子）保持从左到右的顺序。
        // TreeMap 按偏移升序，正好满足「识别按原文先后、插回按倒序」两个方向的要求。
        Map<Integer, List<Slot>> byOffset = new TreeMap<>();
        for (Placement placement : candidates) {
            Integer offset = findInsertOffset(placement, anchors);
            if (offset == null) {
                stats.unplaced++;
                continue;
            }
            byOffset.computeIfAbsent(offset, key -> new ArrayList<>()).add(new Slot(placement, offset));
        }

        List<List<Slot>> groups = new ArrayList<>(byOffset.values());
        List<String> inserts = new ArrayList<>(groups.size());
        for (List<Slot> group : groups) {
            group.sort(Comparator.comparingDouble(slot -> slot.placement.x));
            StringBuilder joined = new StringBuilder();
            for (Slot slot : group) {
                String recognized = recognize(slot.placement, config, stats);
                if (recognized == null) {
                    continue;
                }
                if (!joined.isEmpty()) {
                    joined.append(' ');
                }
                joined.append(recognized);
            }
            inserts.add(joined.toString());
        }
        StringBuilder spliced = new StringBuilder(pageText);
        for (int i = groups.size() - 1; i >= 0; i--) {
            String insert = inserts.get(i);
            if (insert.isEmpty()) {
                continue;
            }
            int offset = Math.max(0, Math.min(spliced.length(), groups.get(i).get(0).offset));
            spliced.insert(offset, insert);
        }
        return spliced.toString();
    }

    /**
     * 级联识别一个片段：本地 → 升级云端。
     *
     * @return 可直接插回的文本（含 {@code $...$}），未认出返回 null
     */
    private String recognize(Placement placement, CrawlerProperties.Formula config, Stats stats) {
        Path fragment = null;
        try {
            BufferedImage image = placement.image.getImage();
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                stats.missed++;
                return null;
            }
            if (pixels(image, 1) > MAX_FRAGMENT_PIXELS * 4) {
                // 原图上千万像素却摆在 20pt 的位置，那是装饰图/校徽不是公式：识别它既慢又没意义
                stats.missed++;
                return null;
            }
            fragment = writeTempImage(image, config.getUpscale());
            String local = recognizeLocal(fragment, config, stats);
            if (local != null) {
                stats.local++;
                stats.recovered++;
                return wrap(local);
            }
            int cloudLimit = Math.max(0, config.getMaxCloudPerDocument());
            if (stats.cloudUsed >= cloudLimit || !ocrService.channelAvailable(OcrService.CHANNEL_CLOUD)) {
                // 与「升级了但没认出来」分开计：前者是预算/配置问题，后者才是识别能力问题，
                // 混在一起会让人去调识别参数，而真正该调的是配额
                stats.cloudBlocked++;
                stats.missed++;
                return null;
            }
            stats.cloudUsed++;
            String cloud = recognizeCloud(fragment, config);
            if (cloud != null) {
                stats.cloud++;
                stats.recovered++;
                return wrap(cloud);
            }
            stats.missed++;
            return null;
        } catch (Exception e) {
            // 一张片段认不出来不该影响整份文档：正文是文本层，本来就还在
            stats.missed++;
            log.debug("公式片段识别失败，跳过：{}", e.getMessage());
            return null;
        } finally {
            deleteQuietly(fragment);
        }
    }

    /**
     * 本地通道：可用且「不为空 + 置信度达标」才算数。
     *
     * <p>置信度不达标就返回 null 让上层升级云端——这正是级联的意义：
     * 本地在公式上经常给出 0.6 上下的结果，看着像字，其实是猜的。
     */
    private String recognizeLocal(Path fragment, CrawlerProperties.Formula config, Stats stats) {
        if (!ocrService.channelAvailable(OcrService.CHANNEL_LOCAL)) {
            return null;
        }
        stats.localCalls++;
        try {
            OcrText recognized = ocrService.recognizeOn(OcrService.CHANNEL_LOCAL, fragment, null);
            if (recognized.blank()
                    || (recognized.score() != null && recognized.score() < config.getEscalateBelow())) {
                return null;
            }
            return sanitize(recognized.text(), config.getMaxOutputChars(), false);
        } catch (Exception e) {
            log.debug("本地通道识别片段失败：{}", e.getMessage());
            return null;
        }
    }

    private String recognizeCloud(Path fragment, CrawlerProperties.Formula config) {
        try {
            OcrText recognized = ocrService.recognizeOn(OcrService.CHANNEL_CLOUD, fragment, config.getPrompt());
            return sanitize(recognized.text(), config.getMaxOutputChars(), true);
        } catch (Exception e) {
            log.debug("云端通道识别片段失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 清洗识别文本：剥掉代码块与定界符、压平换行，并挡掉两类明显不是公式的产物
     * （过长的多半在解释题目，中文过多的说明它输出的是一句话）。
     */
    private String sanitize(String raw, int maxChars, boolean latex) {
        if (raw == null) {
            return null;
        }
        String text = raw.replace("```", " ");
        text = text.replaceFirst("(?i)^\\s*(latex|tex)\\s*[:：]?\\s*", " ");
        if (latex) {
            text = text.replaceAll("\\$+", " ")
                    .replace("\\(", " ").replace("\\)", " ")
                    .replace("\\[", " ").replace("\\]", " ");
        }
        text = text.replaceAll("\\s+", " ").trim();
        if (text.isEmpty() || text.length() > Math.max(1, maxChars) || cjkCount(text) > 6) {
            return null;
        }
        return text;
    }

    /**
     * 公式定界符按项目既有约定 {@code $...$}（前端 RichContent 用 KaTeX 就地渲染，失败则原样显示）。
     *
     * <p>含中文就不包了：KaTeX 解析必失败，届时页面上会多出一对没有意义的 {@code $}。
     */
    private String wrap(String text) {
        return cjkCount(text) == 0 ? "$" + text + "$" : text;
    }

    private int cjkCount(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) {
                count++;
            }
        }
        return count;
    }

    /** 片段放大 + 补白边后写成临时 PNG（文件名纯 ASCII，避免 OCR 原生层遇到中文路径） */
    private Path writeTempImage(BufferedImage source, int configuredUpscale) throws IOException {
        int scale = scaleOf(source, configuredUpscale);
        int width = source.getWidth() * scale;
        int height = source.getHeight() * scale;
        BufferedImage canvas = new BufferedImage(width + PADDING * 2, height + PADDING * 2,
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            // 带 alpha 的图直接写 PNG 会得到透明底，OCR 侧读到的是全 0 像素；画到白底上再写
            g.drawImage(source, PADDING, PADDING, width, height, null);
        } finally {
            g.dispose();
        }
        Path file = Files.createTempFile("zk-formula-", ".png");
        ImageIO.write(canvas, "png", file.toFile());
        return file;
    }

    /**
     * 片段放大倍数：先保证「够 OCR 看」，再保证「不把内存放大没了」。
     *
     * <p>放置尺寸小不代表像素小：一张 3000x2000 的扫描图完全可以被摆在 20pt 的位置上
     * （校徽、装饰条常见这种），按 8 倍放大会直接要几百 MB 画布。
     */
    private int scaleOf(BufferedImage source, int configuredUpscale) {
        int needed = (int) Math.ceil(MIN_OCR_HEIGHT / (double) Math.max(1, source.getHeight()));
        int scale = Math.min(Math.max(Math.max(1, configuredUpscale), needed), MAX_UPSCALE);
        while (scale > 1 && pixels(source, scale) > MAX_FRAGMENT_PIXELS) {
            scale--;
        }
        return scale;
    }

    private long pixels(BufferedImage source, int scale) {
        return (long) source.getWidth() * scale * source.getHeight() * scale;
    }

    /**
     * 找片段的插入偏移：同一行内、位于图片右侧最近的那段文字之前。
     *
     * <p>「同一行」用文字基线与图片竖向区间的重叠判定：PDF 给文字的是一行的<b>基线</b> y，
     * 给图片的是<b>顶边</b> y，两个语义必须换算，不能直接比数值。
     * 行内没有右侧文字（公式在行末）就退化成插在该行末尾；整行都没文字则放弃（没有可靠位置）。
     */
    private Integer findInsertOffset(Placement placement, List<Anchor> anchors) {
        double imageTop = placement.yTop;
        double imageBottom = placement.yTop + placement.height;
        double imageRight = placement.x + placement.width;
        double tolerance = Math.max(2, placement.height * 0.2);

        Anchor rightNearest = null;
        Anchor lineLast = null;
        for (Anchor anchor : anchors) {
            if (!onSameLine(anchor, placement)) {
                continue;
            }
            if (anchor.x + tolerance >= imageRight
                    && (rightNearest == null || anchor.x < rightNearest.x)) {
                rightNearest = anchor;
            }
            if (lineLast == null || anchor.x > lineLast.x) {
                lineLast = anchor;
            }
        }
        if (rightNearest != null) {
            return rightNearest.start;
        }
        return lineLast == null ? null : lineLast.end;
    }

    private boolean onSameLine(Anchor anchor, Placement placement) {
        double ascent = Math.max(3.5, anchor.fontSize * 0.78);
        double descent = Math.max(1.5, anchor.fontSize * 0.25);
        double lineTop = anchor.y - ascent;
        double lineBottom = anchor.y + descent;
        double overlap = Math.min(lineBottom, placement.yTop + placement.height)
                - Math.max(lineTop, placement.yTop);
        if (overlap <= 0) {
            return false;
        }
        double imageCenter = placement.yTop + placement.height / 2;
        return Math.abs(imageCenter - anchor.y) <= Math.max(6, placement.height * 0.9 + anchor.fontSize);
    }

    /** 收集一页里的公式候选（按放置尺寸筛，并按文档级上限截断） */
    private List<Placement> collectCandidates(PDPage page, int pageNumber,
                                              CrawlerProperties.Formula config, Stats stats, int remaining) {
        ImagePlacementCollector collector = new ImagePlacementCollector(page);
        try {
            collector.processPage(page);
        } catch (Exception e) {
            log.warn("PDF 第 {} 页图形层遍历失败，该页不做公式回插：{}", pageNumber, e.getMessage());
            return List.of();
        }
        List<Placement> candidates = new ArrayList<>();
        for (Placement placement : collector.placements) {
            if (placement.height < config.getMinHeight() || placement.height > config.getMaxHeight()
                    || placement.width < config.getMinWidth() || placement.width > config.getMaxWidth()) {
                continue;
            }
            candidates.add(placement);
        }
        // 阅读顺序：自上而下、同行自左而右。成本闸要优先给靠前的式子，而不是内容流里先出现的那张
        candidates.sort(Comparator.comparingDouble((Placement p) -> round(p.yTop))
                .thenComparingDouble(p -> round(p.x)));
        if (remaining <= 0) {
            stats.capped += candidates.size();
            return List.of();
        }
        if (candidates.size() > remaining) {
            stats.capped += candidates.size() - remaining;
            candidates = new ArrayList<>(candidates.subList(0, remaining));
        }
        stats.candidates += candidates.size();
        return candidates;
    }

    private double round(double value) {
        return Math.round(value * 10) / 10.0;
    }

    private boolean isRotated(PDPage page) {
        return page.getRotation() % 360 != 0;
    }

    /** 与普通取字完全一致的一页文本（用于「没有候选」「页面旋转」这些不做回插的页） */
    private String readPlainText(PDDocument document, int page) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        return stripper.getText(document);
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
            // 临时片段图删不掉是操作系统的事
        }
    }

    private String buildNote(Stats stats) {
        StringBuilder note = new StringBuilder("公式片段 ").append(stats.candidates).append(" 处 · 插回 ")
                .append(stats.recovered);
        // 逐层写成「认出/调用」：只看认出数会误判该调哪个旋钮——本地 4/42 是识别能力问题，
        // 云端 0/30 是预算或配置问题，两者的修法完全不同
        note.append("（本地 ").append(stats.local).append('/').append(stats.localCalls)
                .append(" · 云端 ").append(stats.cloud).append('/').append(stats.cloudUsed).append("）");
        if (stats.missed > 0) {
            note.append(" · 未认出 ").append(stats.missed);
        }
        if (stats.cloudBlocked > 0) {
            note.append("（其中 ").append(stats.cloudBlocked)
                    .append(" 处未升级：云端不可用或已达单文档上限）");
        }
        if (stats.unplaced > 0) {
            note.append(" · 无可插位置 ").append(stats.unplaced);
        }
        if (stats.capped > 0) {
            note.append(" · 超单文档上限未处理 ").append(stats.capped);
        }
        return note.toString();
    }

    /** 一次文档处理的逐层计数（级联的「每层识别率」就在这里） */
    private static final class Stats {
        private int candidates;
        private int recovered;
        private int local;
        private int cloud;
        private int localCalls;
        private int cloudUsed;
        private int missed;
        private int cloudBlocked;
        private int unplaced;
        private int capped;
    }

    /** 图片对象在页面上的放置矩形（单位 pt，坐标自上而下，与 TextPosition 的 Adjusted 坐标同系） */
    private static final class Placement {
        private final PDImage image;
        private final double x;
        private final double yTop;
        private final double width;
        private final double height;

        Placement(PDImage image, double x, double yTop, double width, double height) {
            this.image = image;
            this.x = x;
            this.yTop = yTop;
            this.width = width;
            this.height = height;
        }
    }

    /** 一段写入文本在输出串里的起止偏移与起始坐标 */
    private static final class Anchor {
        private final int start;
        private final int end;
        private final double x;
        private final double y;
        private final double fontSize;

        Anchor(int start, int end, double x, double y, double fontSize) {
            this.start = start;
            this.end = end;
            this.x = x;
            this.y = y;
            this.fontSize = fontSize;
        }
    }

    /** 片段与它要插入的偏移 */
    private static final class Slot {
        private final Placement placement;
        private final int offset;

        Slot(Placement placement, int offset) {
            this.placement = placement;
            this.offset = offset;
        }
    }

    /**
     * 记录写入偏移的取字器：{@link #read} 返回的字符串与 {@link #anchors} 里的偏移严格对应，
     * 所以插回位置能精确到「哪个字之前」。
     *
     * <p>之所以能这么信任偏移：PDFTextStripper 的所有输出都经由 writeString 落到同一个 Writer，
     * 包括行分隔符与词间空格；每次 writeString 前后各取一次长度，就是这一段文字的真实起止。
     */
    private static final class AnchoredStripper extends PDFTextStripper {
        private final OffsetWriter writer = new OffsetWriter();
        private final List<Anchor> anchors = new ArrayList<>();

        AnchoredStripper() throws IOException {
            setSortByPosition(true);
        }

        String read(PDDocument document, int page) throws IOException {
            anchors.clear();
            writer.reset();
            setStartPage(page);
            setEndPage(page);
            writeText(document, writer);
            return writer.text();
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            int start = writer.length();
            super.writeString(text, textPositions);
            writer.flush();
            int end = writer.length();
            if (textPositions != null && !textPositions.isEmpty()) {
                TextPosition first = textPositions.get(0);
                double fontSize = clampFontSize(first.getFontSize());
                anchors.add(new Anchor(start, Math.max(start, end),
                        first.getXDirAdj(), first.getYDirAdj(), fontSize));
            }
        }

        private double clampFontSize(float fontSize) {
            if (fontSize <= 0f || Float.isNaN(fontSize) || Float.isInfinite(fontSize)) {
                return 10.5;
            }
            return Math.min(60d, Math.max(4d, fontSize));
        }
    }

    /** 只统计长度的 Writer：正文与偏移同源，不存在「两份文本对不上」的中间态 */
    private static final class OffsetWriter extends Writer {
        private final StringBuilder text = new StringBuilder();

        @Override
        public void write(char[] buffer, int offset, int length) {
            text.append(buffer, offset, length);
        }

        @Override
        public void flush() {
            // 没有缓冲，无需刷新
        }

        @Override
        public void close() {
            // 生命周期跟着一次取字，由调用方控制
        }

        int length() {
            return text.length();
        }

        String text() {
            return text.toString();
        }

        void reset() {
            text.setLength(0);
        }
    }

    /**
     * 图形层遍历：只关心「这一页在什么位置画了哪张图」。
     *
     * <p>其余图形算子一律空实现——公式定位不需要路径、裁剪与颜色，
     * 多实现一个方法就多一处会出错的地方。
     */
    private static final class ImagePlacementCollector extends PDFGraphicsStreamEngine {
        private final List<Placement> placements = new ArrayList<>();
        private final double cropX;
        private final double cropY;
        private final double cropHeight;
        private final boolean rotated;

        ImagePlacementCollector(PDPage page) {
            super(page);
            PDRectangle crop = page.getCropBox() == null ? page.getMediaBox() : page.getCropBox();
            this.cropX = crop.getLowerLeftX();
            this.cropY = crop.getLowerLeftY();
            this.cropHeight = crop.getHeight();
            this.rotated = page.getRotation() % 360 != 0;
        }

        @Override
        public void drawImage(PDImage image) {
            if (rotated || image == null) {
                return;
            }
            Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
            // 带旋转/错切的 CTM 推不出放置矩形的宽高（单位方框被转倒了），这类图不当公式候选
            if (Math.abs(ctm.getValue(0, 1)) > 0.01f || Math.abs(ctm.getValue(1, 0)) > 0.01f) {
                return;
            }
            double scaleX = ctm.getScaleX();
            double scaleY = ctm.getScaleY();
            double translateX = ctm.getTranslateX();
            double translateY = ctm.getTranslateY();
            double topUserSpace = Math.max(translateY, translateY + scaleY);
            placements.add(new Placement(image,
                    translateX - cropX,
                    // PDF 用户空间原点在世界左下、y 向上，文字坐标自上而下：顶边 = 页高 − 顶端的用户空间 y
                    cropHeight - (topUserSpace - cropY),
                    Math.abs(scaleX), Math.abs(scaleY)));
        }

        @Override
        public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
            // 与图片定位无关
        }

        @Override
        public void clip(int windingRule) {
            // 与图片定位无关
        }

        @Override
        public void moveTo(float x, float y) {
            // 与图片定位无关
        }

        @Override
        public void lineTo(float x, float y) {
            // 与图片定位无关
        }

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
            // 与图片定位无关
        }

        @Override
        public Point2D getCurrentPoint() {
            return new Point2D.Float();
        }

        @Override
        public void closePath() {
            // 与图片定位无关
        }

        @Override
        public void endPath() {
            // 与图片定位无关
        }

        @Override
        public void strokePath() {
            // 与图片定位无关
        }

        @Override
        public void fillPath(int windingRule) {
            // 与图片定位无关
        }

        @Override
        public void fillAndStrokePath(int windingRule) {
            // 与图片定位无关
        }

        @Override
        public void shadingFill(org.apache.pdfbox.cos.COSName patternName) {
            // 与图片定位无关
        }
    }
}
