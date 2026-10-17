package com.zhikao.modules.crawler.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 爬虫任务回显对象：补来源名，供任务列表与轮询查看进度漏斗。
 */
@Data
public class CrawlTaskVO {

    private Long id;
    private Long sourceId;
    private String sourceName;
    private String status;
    private Integer fetched;
    private Integer cleaned;
    private Integer staged;
    private String error;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
