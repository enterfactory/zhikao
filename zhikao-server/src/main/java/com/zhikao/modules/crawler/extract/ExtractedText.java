package com.zhikao.modules.crawler.extract;

/**
 * 文档抽取结果：纯文本 + 取到它的通道 + 抽取层对这次取字的自述（{@link ExtractTrace}）。
 *
 * <p>{@code via} 刻意保留下来——「同一段文字是 PDF 自带文本层还是 OCR 认出来的」，
 * 决定了后面该不该给它打「可能识别有误」的质量标记：文本层是精确的，OCR 是概率的。
 *
 * <p>置信度、逐层识别计数、取字说明都在 trace 里，理由见 {@link ExtractTrace}：
 * <b>只有抽取层知道自己丢过什么</b>，下游解析器只看得见文本，看不见文本里原本缺了一块，
 * 所以这句自述必须一路带到解析日志并落库。
 *
 * @param text  抽取到的文本（永不为 null，无内容时为空串）
 * @param via   抽取通道：TEXT（原生文本层/纯文本文件）| OCR（图像识别）
 * @param trace 抽取层自述（通道链、置信度、公式逐层计数、一句话说明）；永不为 null
 */
public record ExtractedText(String text, String via, ExtractTrace trace) {

    public static final String TEXT = "TEXT";
    public static final String OCR = "OCR";

    /** 文本层/纯文本产物（没发生任何 OCR） */
    public static ExtractedText ofText(String text) {
        return new ExtractedText(text, TEXT, ExtractTrace.TEXT_ONLY);
    }

    /** 文本层产物 + 取字说明（如公式片段回插结果） */
    public static ExtractedText ofText(String text, String note) {
        return new ExtractedText(text, TEXT, ExtractTrace.text(note));
    }

    /** 文本层产物 + 完整自述（公式片段级联走这条） */
    public static ExtractedText ofText(String text, ExtractTrace trace) {
        return new ExtractedText(text, TEXT, trace);
    }

    /** OCR 产物：置信度与该次实际使用的通道一起带上 */
    public static ExtractedText ofOcr(String text, Float ocrScore, String channels) {
        return new ExtractedText(text, OCR, ExtractTrace.ocr(channels, ocrScore));
    }

    public boolean ocr() {
        return OCR.equals(via);
    }

    public Float ocrScore() {
        return trace.ocrScore();
    }

    public String note() {
        return trace.note();
    }
}
