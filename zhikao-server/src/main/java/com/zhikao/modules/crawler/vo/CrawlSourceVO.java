package com.zhikao.modules.crawler.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 爬虫来源回显对象：在实体基础上补默认科目名，供列表展示。
 */
@Data
public class CrawlSourceVO {

    private Long id;
    private String name;
    private String baseUrl;
    private String sourceType;
    private String licenseNote;
    private Integer rateLimitMs;
    private Long defaultSubjectId;
    /** 默认科目名（回填，便于展示） */
    private String defaultSubjectName;
    private Integer enabled;
    private String remark;
    private LocalDateTime createTime;
}
