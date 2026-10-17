package com.zhikao.modules.crawler.extract;

import com.zhikao.modules.crawler.extract.formula.InlineFormulaResolver;

/**
 * 抽取层对一份文档的自述（七期 S2）：<b>哪几层真干过活、每层认出多少</b>。
 *
 * <p>为什么要有这个类型而不是继续往 {@link ExtractedText} 上堆字段：文本之外的这几个数
 * 是一个整体——「通道链 + 置信度 + 公式逐层计数 + 一句话说明」共同回答
 * 「这份文档的取字过程发生了什么」。它们必须来自同一次抽取、一起落库；
 * 拆成四个散装参数在流水线里传，早晚会传出一个「计数是新的、通道是旧的」的中间态。
 *
 * <p>{@code channels} 落库后可以 {@code group by}，这是它存在的唯一理由：
 * 「多少份文档本地就兜住了、多少份必须花钱升云端」是采集成本与升级率的原始数据，
 * 而这些数据只有抽取层拿得到。
 *
 * @param channels          本次真调用过的 OCR 通道，按首次调用顺序逗号分隔（如 {@code rapidocr,qwen-vl}）；
 *                          纯文本层、一次 OCR 都没发生时为 null
 * @param ocrScore          整页 OCR 的平均置信度（0~1）；文本层来源为 null
 * @param formulaCandidates 判定为公式片段的内联小图数（0=这份文档没做过片段级重认）
 * @param formulaRecovered  成功插回正文的片段数
 * @param formulaLocal      其中本地通道认出的
 * @param formulaCloud      其中升级云端后认出的
 * @param formulaCloudCalls 实际发给云端的次数（按次计费，与 formulaCloud 的差就是白花的）
 * @param note              给人看的一句话结论（进「解析日志」的质量构成）
 */
public record ExtractTrace(String channels, Float ocrScore, int formulaCandidates, int formulaRecovered,
                           int formulaLocal, int formulaCloud, int formulaCloudCalls, String note) {

    /** 文本层直接取字、没发生任何 OCR */
    public static final ExtractTrace TEXT_ONLY = new ExtractTrace(null, null, 0, 0, 0, 0, 0, null);

    /** 文本层取字 + 一句自述（如「没找到公式片段」之外没什么可说的，note 为 null 时就是 TEXT_ONLY） */
    public static ExtractTrace text(String note) {
        return note == null ? TEXT_ONLY : new ExtractTrace(null, null, 0, 0, 0, 0, 0, note);
    }

    /** 整页 OCR 产物：通道名由 OcrService 盖在结果上，逐页聚合后传进来 */
    public static ExtractTrace ocr(String channels, Float score) {
        return new ExtractTrace(channels, score, 0, 0, 0, 0, 0, null);
    }

    /** 文本层 + 公式片段级联：级联的逐层计数原样搬进文档行 */
    public static ExtractTrace formulas(InlineFormulaResolver.Outcome outcome) {
        return new ExtractTrace(outcome.channels(), null, outcome.candidates(), outcome.recovered(),
                outcome.local(), outcome.cloud(), outcome.cloudCalls(), outcome.note());
    }

    /** 逐页整页 OCR 时把不同通道的名字并起来，保持首次出现的顺序（本地在前、云端在后） */
    public static String mergeChannels(String existing, String added) {
        if (added == null || added.isBlank()) {
            return existing;
        }
        if (existing == null || existing.isBlank()) {
            return added;
        }
        for (String part : existing.split(",")) {
            if (part.equals(added)) {
                return existing;
            }
        }
        return existing + "," + added;
    }
}
