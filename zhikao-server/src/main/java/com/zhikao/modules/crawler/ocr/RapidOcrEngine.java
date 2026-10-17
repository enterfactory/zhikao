package com.zhikao.modules.crawler.ocr;

import com.benjaminwan.ocrlibrary.OcrResult;
import com.benjaminwan.ocrlibrary.Point;
import com.benjaminwan.ocrlibrary.TextBlock;
import com.zhikao.common.BusinessException;
import io.github.mymonstercat.Model;
import io.github.mymonstercat.ocr.InferenceEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 离线 OCR 通道：RapidOCR（PaddleOCR 模型的 ONNX 推理版）。
 *
 * <p>选它的理由就三条，都很实在：
 * <ol>
 *   <li>原生库与模型都在 Maven 依赖的 jar 里，运行期<b>不联网、不下模型</b>——
 *       内网部署、断网演示都能跑；</li>
 *   <li>中文印刷体识别质量够用于题库（PP-OCRv4 中文主模型）；</li>
 *   <li>纯 Java 调用，不需要额外起一个 Python OCR 服务，省掉一整套运维。</li>
 * </ol>
 *
 * <p><b>可用性是探出来的，不是假设的</b>：首次使用时写一张 8x8 空白图真跑一次 {@code runOcr}，
 * 以此触发「加载原生库 → 解包模型 → 初始化引擎」全链路；任一环失败（缺 dll、平台不匹配、
 * 模型解包失败）就记住不可用并降级到云端通道。之所以不直接调 {@code getVersion()}：
 * 原生库是在 {@code runOcr} 内部才加载的，探不到真正的可用性。
 *
 * <p>识别调用整体串行（{@code synchronized}）：底层是原生推理引擎，官方未承诺并发安全，
 * 采集任务本身已经是异步线程池里跑的，串行换稳定是划算的。
 */
@Component
public class RapidOcrEngine implements OcrEngine {

    private static final Logger log = LoggerFactory.getLogger(RapidOcrEngine.class);

    private volatile InferenceEngine engine;
    private volatile boolean probeFailed;
    private final Object initLock = new Object();

    @Override
    public String name() {
        return "rapidocr";
    }

    @Override
    public boolean available() {
        return prepare() != null;
    }

    @Override
    public OcrText recognize(Path imagePath) {
        InferenceEngine current = prepare();
        if (current == null) {
            throw new BusinessException("离线 OCR 不可用（原生库或模型加载失败）");
        }
        Path absolute = imagePath.toAbsolutePath().normalize();
        Path staged = null;
        synchronized (current) {
            OcrResult result;
            try {
                // 原生层接不住非 ASCII 路径（实测「验证-试卷照片.png」变成 ???-??????.png
                // → Target image not found → 识别结果为空），而摄取目录与上传文件名基本都带中文。
                // 这里固定复制到一个纯 ASCII 的临时文件名再识别。
                staged = asciiStaging(absolute);
                result = current.runOcr(staged.toString());
            } catch (Throwable t) {
                // 原生层偶尔会抛 Error（如内存不足、模型断言失败），不能让它掀翻整条流水线
                throw new BusinessException("离线 OCR 识别失败：" + t.getMessage());
            } finally {
                // 只删自己复制出来的临时件；路径本来就合规时 staged 就是原文件，绝不能删
                if (staged != null && !staged.equals(absolute)) {
                    try {
                        Files.deleteIfExists(staged);
                    } catch (Exception ignored) {
                        // 临时文件删不掉不影响识别结果
                    }
                }
            }
            OcrText recognized = joinLines(result);
            if (recognized.blank()) {
                log.info("离线 OCR 未识别到文字：{}", absolute.getFileName());
                return OcrText.empty();
            }
            return recognized;
        }
    }

    /**
     * 把待识别图片换成「纯 ASCII 路径」的临时副本；路径本来就全 ASCII 时原样返回（不多复制一次）。
     *
     * <p>临时文件建在 {@code java.io.tmpdir} 下并沿用原扩展名：RapidOCR 内部按内容解码，
     * 扩展名只是给可能的格式判断留个线索，jpg 也不会被当成 png 处理。
     */
    private Path asciiStaging(Path source) throws Exception {
        String location = source.toString();
        if (isAscii(location)) {
            return source;
        }
        String extension = "";
        String fileName = source.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && dot < fileName.length() - 1) {
            extension = fileName.substring(dot);
        }
        Path staged = Files.createTempFile("zk-ocr-", extension.isEmpty() ? ".img" : extension);
        Files.copy(source, staged, StandardCopyOption.REPLACE_EXISTING);
        return staged;
    }

    private static boolean isAscii(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }

    /**
     * 按检测框复原「行」：竖直方向有重叠的框算同一行，同行内按 x 排序，行间换行。
     *
     * <p>两个理由都来自实测：
     * <ul>
     *   <li>{@code OcrResult.getStrRes()} 把所有块直接连成一行，「一行一选项」的结构没了
     *       （{@code A.13B.26C.40D.15} 粘成一片），按行切题的解析器就认不出选项；</li>
     *   <li>检测框的返回顺序也不可靠——同一行四个选项的框高度略不同，纯按 y 排序会把
     *       {@code A B C D} 排成 {@code A C D B}，选项字母与内容错位比丢选项更难发现。</li>
     * </ul>
     * 「竖直投影有重叠即同行」是文档 OCR 里常规的行重建做法：重叠比例阈值取 0.5，
     * 既能把同一行的选项聚在一起，又不会把上下两行题干并成一行。
     *
     * <p>顺带算出<b>各框识别分的平均值</b>：这是引擎白送的质检信号，
     * 用来判断这份 OCR 结果该不该送去更强（更贵）的通道重认。
     * 全为 0 时视为「引擎没给分」返回 null，而不是把 0 当成「识别得很差」——
     * 前者只是没数据，后者会误导人去重跑整份文档。
     */
    private OcrText joinLines(OcrResult result) {
        if (result == null) {
            return OcrText.empty();
        }
        List<TextBlock> blocks = result.getTextBlocks();
        List<Box> boxes = new ArrayList<>();
        if (blocks != null) {
            for (TextBlock block : blocks) {
                String text = block.getText() == null ? "" : block.getText().trim();
                Box box = Box.of(block);
                if (box != null && !text.isEmpty()) {
                    box.text = text;
                    boxes.add(box);
                }
            }
        }
        if (boxes.isEmpty()) {
            // 没有可用坐标（极端情况：引擎只给了合并文本）时退回原样输出，至少不丢字
            return OcrText.of(result.getStrRes());
        }
        boxes.sort(Comparator.comparingInt(box -> box.centerY));
        List<Row> rows = new ArrayList<>();
        for (Box box : boxes) {
            Row row = rows.isEmpty() ? null : rows.get(rows.size() - 1);
            // 与「整行的竖直跨度」比重叠，而不是与行内最后一个框比：
            // 后者会靠逐个框的微小错位把上下两道题串成一行（连锁合并）
            if (row == null || !row.overlaps(box)) {
                row = new Row();
                rows.add(row);
            }
            row.add(box);
        }
        StringBuilder text = new StringBuilder();
        for (Row row : rows) {
            text.append(row.join(' ')).append('\n');
        }
        return new OcrText(text.toString().trim(), averageScore(boxes));
    }

    /** 各文本框识别分的平均值；引擎没给分（全 0）时返回 null */
    private Float averageScore(List<Box> boxes) {
        float sum = 0f;
        for (Box box : boxes) {
            sum += box.score;
        }
        if (sum <= 0f) {
            return null;
        }
        return Math.round(sum / boxes.size() * 10000f) / 10000f;
    }

    /** 一行：竖直方向重叠的框聚在一起，按 x 排序后拼成一行文本 */
    private static final class Row {
        private final List<Box> boxes = new ArrayList<>();
        private int top = Integer.MAX_VALUE;
        private int bottom = Integer.MIN_VALUE;

        void add(Box box) {
            boxes.add(box);
            top = Math.min(top, box.top);
            bottom = Math.max(bottom, box.bottom);
        }

        /** 新框与本行的重叠高度达到「本行高度与新框高度里较矮者」的一半，就算同一行 */
        boolean overlaps(Box box) {
            int overlap = Math.min(bottom, box.bottom) - Math.max(top, box.top);
            int shorter = Math.min(bottom - top, box.bottom - box.top);
            return shorter <= 0 ? Math.abs(box.centerY - (top + bottom) / 2) < 10 : overlap * 2 >= shorter;
        }

        String join(char separator) {
            boxes.sort(Comparator.comparingInt(box -> box.left));
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < boxes.size(); i++) {
                if (i > 0) {
                    line.append(separator);
                }
                line.append(boxes.get(i).text);
            }
            return line.toString();
        }
    }

    /** 检测框的最小包围盒 + 文本 + 该框的识别分 */
    private static final class Box {
        private int top;
        private int bottom;
        private int left;
        private int centerY;
        private String text = "";
        private float score;

        static Box of(TextBlock block) {
            List<Point> points = block.getBoxPoint();
            if (points == null || points.isEmpty()) {
                return null;
            }
            Box box = new Box();
            box.top = Integer.MAX_VALUE;
            box.bottom = Integer.MIN_VALUE;
            box.left = Integer.MAX_VALUE;
            for (Point point : points) {
                box.top = Math.min(box.top, point.getY());
                box.bottom = Math.max(box.bottom, point.getY());
                box.left = Math.min(box.left, point.getX());
            }
            box.centerY = (box.top + box.bottom) / 2;
            box.score = block.getBoxScore();
            return box;
        }
    }

    /** 懒初始化 + 失败记忆：探测成功返回引擎，任一步失败返回 null（不抛） */
    private InferenceEngine prepare() {
        InferenceEngine current = engine;
        if (current != null || probeFailed) {
            return current;
        }
        synchronized (initLock) {
            if (engine != null || probeFailed) {
                return engine;
            }
            Path probe = null;
            try {
                InferenceEngine instance = InferenceEngine.getInstance(Model.ONNX_PPOCR_V4);
                probe = Files.createTempFile("zk-ocr-probe", ".png");
                ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", probe.toFile());
                instance.runOcr(probe.toString());   // 真跑一次，把加载链路走通
                engine = instance;
                log.info("离线 OCR（RapidOCR / PP-OCRv4 ONNX）就绪");
                return engine;
            } catch (Throwable t) {
                probeFailed = true;
                log.warn("离线 OCR 不可用，将由云端通道接管：{}", t.getMessage());
                return null;
            } finally {
                if (probe != null) {
                    try {
                        Files.deleteIfExists(probe);
                    } catch (Exception ignored) {
                        // 临时探测文件删不掉不影响任何事
                    }
                }
            }
        }
    }
}
