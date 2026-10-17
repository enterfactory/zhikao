package com.zhikao.modules.crawler.ocr;

/**
 * 一次 OCR 识别的结果：文本 + 平均置信度 + 干这件事的引擎（七期「识别质量可统计」的最小载体）。
 *
 * <p>为什么要把置信度带出来而不是只返回字符串：识别层后面的判断全靠它——
 * 「这批字是 0.95 认出来的」和「0.62 认出来的」，该不该升级更强的通道、
 * 该不该让人审重点核对，答案完全不同。RapidOCR 每个文本框自带识别分，
 * 之前只取了 {@code getText()} 把这个数丢了，等于白扔一个免费的质检信号。
 *
 * <p>{@code engine} 由 {@link OcrService} 在调用返回后盖章，引擎自己不用管：
 * 「这份文档是哪条通道认出来的」只有通道选择器知道，而这句话是分层识别统计（S2）
 * 唯一的取数点——没有它，页面上就只能说「走了 OCR」，说不清本地兜住了还是花钱升了云端。
 *
 * @param text   识别出的文本（永不为 null，图上确实没字时为空串）
 * @param score  各文本框识别分的平均值（0~1）；引擎不提供时（如大模型通道）为 null
 * @param engine 完成识别的通道名（{@link OcrService#CHANNEL_LOCAL} 等）；未经通道选择器时为 null
 */
public record OcrText(String text, Float score, String engine) {

    public OcrText(String text, Float score) {
        this(text, score, null);
    }

    public static OcrText of(String text) {
        return new OcrText(text == null ? "" : text, null, null);
    }

    /** 没识别到字时置信度没有意义，一并置 null（避免前端显示「0% 置信度」这种误导） */
    public static OcrText empty() {
        return new OcrText("", null, null);
    }

    public boolean blank() {
        return text == null || text.isBlank();
    }

    /** 盖上通道名（已盖过就不动，避免级联里把「本地认的」改写成「云端」） */
    public OcrText withEngine(String channel) {
        return new OcrText(text, score, engine == null ? channel : engine);
    }
}
