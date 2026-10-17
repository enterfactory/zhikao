package com.zhikao.modules.crawler.ocr;

import com.zhikao.common.BusinessException;
import com.zhikao.config.CrawlerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OCR 通道选择器：把「用哪个引擎」这件事集中到一处。
 *
 * <p>{@code zhikao.crawler.ocr.engine} 三种取值：
 * <ul>
 *   <li>{@code auto}（默认）：离线优先，离线不可用再走云端——离线免费、快、不触网，
 *       云端只兜它兜不住的；</li>
 *   <li>{@code rapid}：强制离线，不可用直接失败（内网/涉密场景要求「绝不出网」）；</li>
 *   <li>{@code qwen}：强制云端。</li>
 * </ul>
 *
 * <p>auto 模式下两个通道都不可用时抛业务异常，错误信息里说清「怎么才能行」，
 * 而不是回退成「识别结果为空」——后者会让使用者以为文档里没题，白折腾一轮。
 *
 * <p>除了按策略选通道，这里还提供 {@link #recognizeOn} 「就指定这一层认一次」的能力位：
 * 公式片段那种「本地先试、不可信再升级云端」的级联必须能主动升级，
 * 而 auto 的语义只在本地<b>不可用</b>时才换通道，本地认错的时候它不会换。
 */
@Component
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private final CrawlerProperties properties;
    private final Map<String, OcrEngine> engines;

    public OcrService(CrawlerProperties properties, RapidOcrEngine rapid, QwenVlOcrEngine qwen) {
        this.properties = properties;
        // LinkedHashMap 保序：auto 模式按「离线 → 云端」的顺序尝试
        this.engines = new LinkedHashMap<>();
        this.engines.put(rapid.name(), rapid);
        this.engines.put(qwen.name(), qwen);
    }

    /** 离线通道名（引擎枚举值，也是级联的第一层） */
    public static final String CHANNEL_LOCAL = "rapidocr";

    /** 云端通道名（级联的第二层，按次计费） */
    public static final String CHANNEL_CLOUD = "qwen-vl";

    /** 按配置的通道策略识别一张图片；返回文本 + 该通道的平均识别置信度（可能为 null） */
    public OcrText recognize(Path imagePath) {
        OcrEngine engine = choose();
        return recognizeWith(engine, imagePath, null);
    }

    /** 指定通道是否可用：级联上层据此决定「这一层能不能试」，绝不抛异常 */
    public boolean channelAvailable(String channel) {
        OcrEngine engine = engines.get(channel);
        return engine != null && engine.available();
    }

    /**
     * 就在这一层识别（不走 auto 策略）。
     *
     * <p>级联需要的是「先只问本地，本地不可信再问云端」，而 auto 的语义是
     * 「本地不可用才换云端」——引擎加载得好好的时候它永远不会升级，
     * 于是「本地认错了」这种情况根本没机会走到更强的通道。
     */
    public OcrText recognizeOn(String channel, Path imagePath, String instruction) {
        OcrEngine engine = engines.get(channel);
        if (engine == null) {
            throw new BusinessException("未知的 OCR 通道：" + channel);
        }
        if (!engine.available()) {
            throw new BusinessException("OCR 通道不可用：" + channel);
        }
        return recognizeWith(engine, imagePath, instruction);
    }

    private OcrText recognizeWith(OcrEngine engine, Path imagePath, String instruction) {
        long start = System.currentTimeMillis();
        OcrText recognized = instruction == null
                ? engine.recognize(imagePath)
                : engine.recognize(imagePath, instruction);
        log.info("OCR 完成：engine={} 文件={} 字符数={} 置信度={} 耗时={}ms",
                engine.name(), imagePath.getFileName(), recognized.text().length(),
                recognized.score(), System.currentTimeMillis() - start);
        // 通道名在这里盖：调用方只知道「认出来了」，不知道是哪条通道认的
        return recognized.withEngine(engine.name());
    }

    /** 当前会真正使用哪个引擎（auto 会做可用性探测，因此首次调用稍慢） */
    public OcrEngine choose() {
        String configured = properties.getOcr().getEngine();
        String mode = configured == null ? "auto" : configured.trim().toLowerCase(Locale.ROOT);
        if ("rapid".equals(mode) || "qwen".equals(mode)) {
            OcrEngine engine = engines.get("rapid".equals(mode) ? CHANNEL_LOCAL : CHANNEL_CLOUD);
            if (engine == null) {
                throw new BusinessException("未知的 OCR 引擎配置：" + configured);
            }
            if (!engine.available()) {
                throw new BusinessException("指定的 OCR 引擎不可用：" + engine.name()
                        + "（rapid 需原生库/模型可加载，qwen 需配置 zhikao.crawler.ocr.qwen.enabled 与 api-key）");
            }
            return engine;
        }
        if (!"auto".equals(mode)) {
            throw new BusinessException("OCR 引擎配置只能是 auto / rapid / qwen，当前为：" + configured);
        }
        List<OcrEngine> candidates = List.of(engines.get(CHANNEL_LOCAL), engines.get(CHANNEL_CLOUD));
        for (OcrEngine engine : candidates) {
            if (engine != null && engine.available()) {
                return engine;
            }
        }
        throw new BusinessException("没有可用的 OCR 通道：离线引擎（RapidOCR）加载失败，"
                + "云端通道（Qwen-VL）未配置。请检查 zhikao.crawler.ocr.* 配置");
    }

    /** 通道状态（供采集面板展示：现在走哪条、另一条为什么不可用） */
    public Map<String, Object> channels() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("mode", properties.getOcr().getEngine());
        for (OcrEngine engine : engines.values()) {
            status.put(engine.name(), engine.available());
        }
        return status;
    }
}
