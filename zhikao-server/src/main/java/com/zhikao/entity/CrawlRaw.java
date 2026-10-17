package com.zhikao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 抓取原文（六期）：抓到的每一条原始内容。
 *
 * <p>dedup_hash 唯一：同一 URL/文件的原文只落一份，重复抓取命中已有记录即可，
 * 这是「多次跑同一来源不产生重复题」的第一道闸（题级去重另在 Cleaner 用内容哈希）。
 * 解析失败的原文也保留在这里不丢弃——原文永远比派生数据值钱，留着可反复重解析。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("crawl_raw")
public class CrawlRaw {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long extractionMillis;

    private String contentHash;
    private String parserVersion;
    private String sourceName;
    private String sourceSnapshot;
    private Integer pageCount;

    /** 所属任务 crawl_task.id */
    private Long taskId;

    /** 来源 URL 或本地文件路径 */
    private String sourceUrl;

    /** SHA-256(规范化来源标识)，唯一去重键 */
    private String dedupHash;

    /** 原始正文（MEDIUMTEXT，容纳整页 HTML/整篇文档） */
    private String content;

    /**
     * 解析结果留痕（七期）：一份文档一个结论，管理页的「解析日志」直接读这几列。
     *
     * <p>为什么放在文档行而不是任务行：一次任务里 20 份文档可能只有 3 份出得了题，
     * 任务级聚合计数（fetched/cleaned/staged）会把「哪一份、为什么」这个唯一有用的信息抹掉。
     * 老数据这几列为 null，前端显示为「采集于解析日志上线前」而不是空值。
     */
    private Integer charCount;

    /** 1=正文来自 OCR；0=文档文本层/纯文本直接读取 */
    private Integer viaOcr;

    /** 认领该文档的解析器名；null 表示没有解析器认领 */
    private String parserName;

    /** 解析器产出的候选题条数 */
    private Integer parsedCount;

    /** 清洗打标后进入待审的条数 */
    private Integer stagedCount;

    /** 本次解析结果说明（为何 0 题 / 解析异常原因） */
    private String parseNote;

    /**
     * 识别质量分（0~100，P1）：由 {@code DocQualityEvaluator} 按「入审率 / 空槽嫌疑 /
     * 答案存在率 / 选项完整率 / 识别置信度」加权算出。
     *
     * <p>刻意不是「人工评分」——它是每份文档每次采集都会自动重算的指标，
     * 用来回答「这份文档认得完不完整」，并为后续「质量不够就升级更强识别通道」提供判据。
     * 老数据为 null，前端显示「-」。
     */
    private Integer qualityScore;

    /** OCR 平均置信度（0~1）；文本层产物或引擎未给分时为 null */
    private Double ocrConfidence;

    /** 质量分构成（人可读，如「入审 22/22 · 答案 0/22 · 空槽嫌疑 6/22 · 文本层」） */
    private String qualityDetail;

    /**
     * 分层识别统计（七期 S2）：这几个数只有抽取层拿得到，落库后就是可统计的列。
     *
     * <p>{@code ocrChannels} 记「这份文档真调用过哪几条 OCR 通道」（按首次调用顺序，逗号分隔），
     * 纯文本层且没触发片段重认时为 null——null 与空串不同，前者是「压根没花 OCR」，
     * 后者不该出现。公式四个计数全为 0/NULL 时页面显示「-」而不是「0/0」，
     * 因为「没有公式候选」和「有候选但一个都没认出来」是两件完全不同的事。
     */
    private String ocrChannels;

    /** 判定为公式片段的内联小图数（区域级重认的输入） */
    private Integer formulaCandidates;

    /** 成功插回正文的公式片段数 */
    private Integer formulaRecovered;

    /** 其中本地通道（RapidOCR）认出的 */
    private Integer formulaLocal;

    /** 其中升级云端通道（Qwen-VL）后认出的 */
    private Integer formulaCloud;

    /** 实际发给云端的次数（按次计费）：与 formulaCloud 的差就是「花了钱没认出来」 */
    private Integer formulaCloudCalls;

    private LocalDateTime fetchTime;
}
