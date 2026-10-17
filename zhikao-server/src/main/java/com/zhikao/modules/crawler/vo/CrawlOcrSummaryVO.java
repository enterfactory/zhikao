package com.zhikao.modules.crawler.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 「识别与成本」概览（S3）：把 S2 落在 {@code crawl_raw} 文档行上的逐层计数汇总成可读的口径。
 *
 * <p>这块面板回答四个问题，字段也按这四问分组，避免堆成一屏无意义的饼图：
 * <ol>
 *   <li><b>字是怎么来的</b>：文本层直取 / 扫描整页 OCR / 文本层里做过片段级重认，各占多少文档；</li>
 *   <li><b>级联每一层捞出多少</b>：候选 → 本地认出 → 升级云端 → 云端认出 → 仍未认出；</li>
 *   <li><b>花了多少钱、值不值</b>：云端调用总次数、真认出多少次、有多少文档把成本闸顶满了；</li>
 *   <li><b>接下来该人工看哪几份</b>：未认出片段最多的文档榜。</li>
 * </ol>
 *
 * <p>所有比率都是 0~1 的小数（保留 4 位），百分化交给前端；分母为 0 时给 {@code null} 而不是 0，
 * 因为「一条候选都没发现」和「发现了但一个都没认出」是两件完全不同的事，页面上必须能分辨。
 */
@Data
public class CrawlOcrSummaryVO {

    /** 时间窗天数；0 表示不限（全部历史文档） */
    private Integer days;

    /** 时间窗起点；days=0 时为 null，前端就不画区间提示 */
    private LocalDateTime since;

    /** 趋势桶：整个窗口内一份文档都没采到时为 false，前端据此显示空态而不是空图 */
    private Boolean empty;

    // ==================== 取字来源分布 ====================

    /** 窗口内文档总数 */
    private Long docTotal;

    /** 正文来自文本层（没做整页 OCR）的文档数 */
    private Long textLayerDocs;

    /** 正文来自整页 OCR 的文档数（扫描版） */
    private Long scanDocs;

    /** 做过公式片段级联重认的文档数（candidates > 0） */
    private Long formulaDocs;

    /** 按「取字方式 × 实际通道」分桶，前端做条形对比 */
    private List<ChannelBucket> channels;

    // ==================== 级联逐层 ====================

    /** 公式片段候选总数（级联的输入） */
    private Long candidates;

    /** 成功插回的片段数 */
    private Long recovered;

    /** 其中本地通道（RapidOCR）认出的 */
    private Long localRecovered;

    /** 其中升级云端（Qwen-VL）后认出的 */
    private Long cloudRecovered;

    /** 实际发给云端的次数（按次计费，与 cloudRecovered 的差就是白花的） */
    private Long cloudCalls;

    /** 仍未认出的片段数 = candidates - recovered */
    private Long unresolved;

    /** 云端调用次数达到成本闸上限的文档数：这些文档的未认出是「没花钱」而不是「认不出」 */
    private Long capHitDocs;

    /** 总插回率 = recovered / candidates */
    private Double recoveryRate;

    /** 本地命中率 = localRecovered / candidates */
    private Double localRate;

    /** 升级率 = cloudCalls / candidates，本地兜不住的比例，也是单位成本 */
    private Double escalateRate;

    /** 云端命中率 = cloudRecovered / cloudCalls */
    private Double cloudRate;

    // ==================== 配置回显 ====================

    /** 当前生效的成本闸（zhikao.crawler.ocr.formula.max-cloud-per-document） */
    private Integer cloudCallCap;

    /** 当前生效的升级阈值（低于该本地分数才升级云端） */
    private Double escalateBelow;

    /** 级联里「升级」用的阈值配置的中文说明，直接给前端当脚注，免得页面与配置两套口径 */
    private String thresholdNote;

    // ==================== 质量 ====================

    /** 平均质量分（0~100），窗口内无打分时为 null */
    private Double qualityAvg;

    /** 整页 OCR 文档的平均置信度（0~1）；与质量分不同维度，不能混着展示 */
    private Double ocrConfidenceAvg;

    /** 质量分档计数 */
    private QualityBuckets quality;

    // ==================== 走势与人工待办 ====================

    /** 按天的级联计数 */
    private List<DailyPoint> daily;

    /** 未认出片段最多的若干份文档 */
    private List<WatchDoc> watchlist;

    /** 「取字方式 × 通道」的一个桶 */
    @Data
    public static class ChannelBucket {

        /** 原始桶键，形如 0|rapidocr,qwen-vl */
        private String key;

        /** 主标签：文本层 / 扫描件 */
        private String label;

        /** 副标签：未走 OCR / 仅本地 / 本地 → 云端 / 云端 / 老数据未留通道 */
        private String subLabel;

        private Long docs;

        /** 占窗口内文档总数的比例（0~1） */
        private Double ratio;
    }

    @Data
    public static class QualityBuckets {

        /** ≥80：可直接放心入审 */
        private Long highDocs;

        /** 60~79：能用，值得抽查 */
        private Long midDocs;

        /** <60：大概率要重采或调解析器 */
        private Long lowDocs;

        /** 未打分（质量分列上线前采集的老数据） */
        private Long unscoredDocs;
    }

    @Data
    public static class DailyPoint {

        /** yyyy-MM-dd */
        private String date;

        private Long docs;
        private Long candidates;
        private Long recovered;
        private Long cloudCalls;

        /** 当天的插回率；当天没有候选时为 null */
        private Double recoveryRate;
    }

    @Data
    public static class WatchDoc {

        private Long id;

        /** 文档名（source_url 的文件名部分） */
        private String docName;

        private Integer viaOcr;
        private String ocrChannels;
        private Integer candidates;
        private Integer recovered;

        /** candidates - recovered，排序键 */
        private Integer unresolved;

        private Integer localRecovered;
        private Integer cloudRecovered;
        private Integer cloudCalls;

        /** 云端额度是否已用满：用满说明剩下的未认出不是能力问题 */
        private Boolean capHit;

        private Integer qualityScore;
        private String parserName;
        private Integer parsedCount;
        private Integer stagedCount;
        private String parseNote;
        private LocalDateTime fetchTime;
    }
}
