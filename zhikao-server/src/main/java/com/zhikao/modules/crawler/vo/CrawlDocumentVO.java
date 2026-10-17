package com.zhikao.modules.crawler.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档解析日志回显对象（七期）：一行 = 一份摄取进来的文档 + 它被解析的结论。
 *
 * <p>页面上「上传成功，新文档 1 份，候选题 0 道」这类看不懂的数字，靠的就是这几列：
 * 取了多少字、走没走 OCR、哪个解析器认领、认出几道题、最后进了待审几道、没进的原因是什么。
 * 详情接口额外带原文片段与该文档产出的候选题，用于人工判断「是文档本身的问题还是解析器的问题」。
 */
@Data
public class CrawlDocumentVO {

    private Long id;

    private String contentHash,parserVersion;
    private Integer pageCount;

    private Long taskId;

    /** 文档名（sourceUrl 的文件名部分），列表主列 */
    private String docName;

    /** 完整来源路径，详情里展示，便于定位到磁盘上的文件 */
    private String sourceUrl;

    /** 1=正文来自 OCR；0=文本层/纯文本直接读取 */
    private Integer viaOcr;

    /** 取字后的正文字符数：为 0 说明文档没读出内容，与「读出了但解析不出题」是两类问题 */
    private Integer charCount;

    /** 认领该文档的解析器名；null 表示没有解析器认领 */
    private String parserName;

    /** 解析器产出的候选题条数 */
    private Integer parsedCount;

    /** 清洗打标后进入待审的条数 */
    private Integer stagedCount;

    /** 本次解析结果说明（为何 0 题 / 解析异常原因） */
    private String parseNote;

    /** 识别质量分（0~100）：自动算出，用于横向比较「哪些文档认得差」；老数据为 null */
    private Integer qualityScore;

    /** 质量分构成（人可读，如「解析 22 · 入审 22/22 · 空槽嫌疑 6/22 · 文本层取字」） */
    private String qualityDetail;

    /** OCR 平均置信度（0~1）；文本层来源为 null，不能当成 0 */
    private Double ocrConfidence;

    /** 分层识别（S2）：这份文档真调用过的 OCR 通道，逗号分隔；一次 OCR 都没发生时为 null */
    private String ocrChannels;

    /** 公式片段候选数（区域级重认的输入）；null=该列上线前采集 */
    private Integer formulaCandidates;

    /** 成功插回的公式片段数，页面显示为「插回/候选」 */
    private Integer formulaRecovered;

    /** 其中本地通道认出的 */
    private Integer formulaLocal;

    /** 其中升级云端后认出的（也就是真正花钱的那部分产出） */
    private Integer formulaCloud;

    /** 实际发给云端的次数：按次计费，与 formulaCloud 的差就是白花的 */
    private Integer formulaCloudCalls;

    private LocalDateTime fetchTime;

    /** 详情接口才返回：原文前若干字符，用于核对取字质量 */
    private String contentPreview;

    /** 详情接口才返回：原文总字数，配合 contentTruncated 说明预览是否被截断 */
    private Integer contentLength;

    /** 详情接口才返回：contentPreview 未覆盖全部原文 */
    private Boolean contentTruncated;

    /** 详情接口才返回：该文档产出的候选题（最多若干条） */
    private List<CrawlQuestionVO> questions;

    /** 详情接口才返回：questions 是否被截断 */
    private Boolean questionsTruncated;
}
