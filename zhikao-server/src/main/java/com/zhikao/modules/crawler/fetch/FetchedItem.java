package com.zhikao.modules.crawler.fetch;

import com.zhikao.modules.crawler.extract.ExtractTrace;

/**
 * 一条采集产物：来源定位（本地文件绝对路径）+ 抽出的原始文本 + 抽取通道 + 抽取层自述。
 * 由 {@link DocumentFetcher} 产出，交给 parse→clean→tag 流水线。
 *
 * <p>{@code via} 取 {@code TEXT}（文件自带文字）或 {@code OCR}（图像识别出来的字）：
 * 后者是概率结果，流水线据此给候选题目追加质量标记，让审核人知道该多瞄一眼。
 *
 * <p>{@code trace} 带着「实际调用过哪条 OCR 通道、置信度、公式片段逐层计数、一句话说明」，
 * 流水线把这几个数原样写进文档行（{@code crawl_raw.ocr_channels} 等列）——
 * 这是「分层识别率」唯一的落库时机，过了抽取层就没人知道了。
 *
 * @param sourceUrl 本地文件绝对路径（也是 dedup 的输入）
 * @param content   取到的正文
 * @param via       TEXT | OCR
 * @param trace     抽取层自述，永不为 null
 */
public record FetchedItem(String sourceUrl, String content, String via, ExtractTrace trace, String snapshotPath, String contentHash, Long extractionMillis) {
    public FetchedItem(String sourceUrl,String content,String via,ExtractTrace trace){this(sourceUrl,content,via,trace,null,null,null);}

    public boolean ocr() {
        return "OCR".equals(via);
    }
}
