package com.zhikao.modules.crawler.vo;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 爬虫漏斗统计：各 stage 的题目条数 + 任务/来源概览，供前端面板。
 */
@Data
public class CrawlStatsVO {

    /** 各阶段计数：RAW/PARSED/CLEANED/TAGGED/REVIEWED/PROMOTED/REJECTED */
    private Map<String, Long> stageCounts = new LinkedHashMap<>();

    /** 来源总数 */
    private long sourceTotal;

    /** 启用来源数 */
    private long sourceEnabled;

    /** 任务总数 */
    private long taskTotal;

    /** 抓取原文总数 */
    private long rawTotal;
}
