package com.zhikao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.entity.CrawlRaw;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 抓取原文 Mapper：标准 CRUD 由 BaseMapper 提供。
 * dedup_hash 上有唯一约束，插入冲突时由服务层捕获并复用已有记录。
 *
 * <p>下面几个聚合查询是「识别与成本」概览（S3）的数据来源。它们只写在报表 SQL 里、
 * 返回 Map 而不返回实体，是因为这里要的是 SUM/COUNT 而不是行；
 * 具体口径的解释与派生比率一律放在服务层，SQL 只负责把数取回来。
 */
@Mapper
public interface CrawlRawMapper extends BaseMapper<CrawlRaw> {

    /**
     * 逐层识别的总量聚合。
     *
     * <p>{@code since} 传 null 表示不限时间窗（全部文档）。
     * <p>{@code cap} 是配置里的「单文档云端调用上限」，用来数出「成本闸顶满」的文档有几份——
     * 这些文档的未认出片段不是识别失败，是没花钱，前端要把两者分开说。
     */
    @Select("""
            SELECT COUNT(*)                                                            AS docTotal,
                   SUM(CASE WHEN via_ocr = 0 THEN 1 ELSE 0 END)                        AS textLayerDocs,
                   SUM(CASE WHEN via_ocr = 1 THEN 1 ELSE 0 END)                        AS scanDocs,
                   SUM(CASE WHEN COALESCE(formula_candidates, 0) > 0 THEN 1 ELSE 0 END) AS formulaDocs,
                   COALESCE(SUM(formula_candidates), 0)                                AS candidates,
                   COALESCE(SUM(formula_recovered), 0)                                 AS recovered,
                   COALESCE(SUM(formula_local), 0)                                     AS localRecovered,
                   COALESCE(SUM(formula_cloud), 0)                                     AS cloudRecovered,
                   COALESCE(SUM(formula_cloud_calls), 0)                               AS cloudCalls,
                   SUM(CASE WHEN COALESCE(formula_cloud_calls, 0) >= #{cap} THEN 1 ELSE 0 END) AS capHitDocs,
                   ROUND(AVG(quality_score), 1)                                        AS qualityAvg,
                   ROUND(AVG(ocr_confidence), 3)                                       AS ocrConfidenceAvg
            FROM crawl_raw
            WHERE #{since,jdbcType=TIMESTAMP} IS NULL OR fetch_time >= #{since,jdbcType=TIMESTAMP}
            """)
    Map<String, Object> ocrSummaryTotals(@Param("since") LocalDateTime since, @Param("cap") int cap);

    /**
     * 取字来源 × 实际用过的 OCR 通道，组合分桶计数。
     * 桶键形如 {@code 0|rapidocr,qwen-vl}（前缀是 via_ocr，竖线后是 ocr_channels，空串表示没走 OCR）。
     */
    @Select("""
            SELECT CONCAT(via_ocr, '|', COALESCE(ocr_channels, '')) AS bucket, COUNT(*) AS docs
            FROM crawl_raw
            WHERE #{since,jdbcType=TIMESTAMP} IS NULL OR fetch_time >= #{since,jdbcType=TIMESTAMP}
            GROUP BY CONCAT(via_ocr, '|', COALESCE(ocr_channels, ''))
            ORDER BY COUNT(*) DESC
            """)
    List<Map<String, Object>> ocrSummaryChannels(@Param("since") LocalDateTime since);

    /** 按天的级联计数：看升级率/成本随时间的走势，以及某天是否有文档把云端额度打满 */
    @Select("""
            SELECT CAST(fetch_time AS DATE)              AS day,
                   COUNT(*)                              AS docs,
                   COALESCE(SUM(formula_candidates), 0)  AS candidates,
                   COALESCE(SUM(formula_recovered), 0)   AS recovered,
                   COALESCE(SUM(formula_cloud_calls), 0) AS cloudCalls
            FROM crawl_raw
            WHERE #{since,jdbcType=TIMESTAMP} IS NULL OR fetch_time >= #{since,jdbcType=TIMESTAMP}
            GROUP BY CAST(fetch_time AS DATE)
            ORDER BY CAST(fetch_time AS DATE)
            """)
    List<Map<String, Object>> ocrSummaryDaily(@Param("since") LocalDateTime since);

    /**
     * 质量分档计数：分桶写死在 SQL 里，只为了让「低质量文档有多少」这句话有数可依。
     * 档界与质量分的构成（入审率 30 + 结构 25 + 答案 15 + 选项 15 + 置信度 15）对齐。
     */
    @Select("""
            SELECT SUM(CASE WHEN quality_score >= 80 THEN 1 ELSE 0 END)              AS highDocs,
                   SUM(CASE WHEN quality_score >= 60 AND quality_score < 80 THEN 1 ELSE 0 END) AS midDocs,
                   SUM(CASE WHEN quality_score < 60 THEN 1 ELSE 0 END)                AS lowDocs,
                   SUM(CASE WHEN quality_score IS NULL THEN 1 ELSE 0 END)             AS unscoredDocs
            FROM crawl_raw
            WHERE #{since,jdbcType=TIMESTAMP} IS NULL OR fetch_time >= #{since,jdbcType=TIMESTAMP}
            """)
    Map<String, Object> ocrSummaryQuality(@Param("since") LocalDateTime since);

    /**
     * 最需要人工看的文档：未认出片段最多的前 N 份。
     * 明确列出字段而不 {@code SELECT *}，是为了不把这批行的 MEDIUMTEXT content 拉进内存。
     */
    @Select("""
            SELECT id, task_id, source_url, via_ocr, parser_name, parsed_count, staged_count,
                   parse_note, quality_score, ocr_confidence, ocr_channels,
                   formula_candidates, formula_recovered, formula_local, formula_cloud,
                   formula_cloud_calls, fetch_time
            FROM crawl_raw
            WHERE COALESCE(formula_candidates, 0) > 0
              AND (#{since,jdbcType=TIMESTAMP} IS NULL OR fetch_time >= #{since,jdbcType=TIMESTAMP})
            ORDER BY (COALESCE(formula_candidates, 0) - COALESCE(formula_recovered, 0)) DESC,
                     COALESCE(formula_candidates, 0) DESC
            LIMIT #{limit}
            """)
    List<CrawlRaw> ocrSummaryWatchlist(@Param("since") LocalDateTime since, @Param("limit") int limit);
}
