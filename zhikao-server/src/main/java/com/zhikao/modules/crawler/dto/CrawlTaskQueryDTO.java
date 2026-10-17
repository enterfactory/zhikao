package com.zhikao.modules.crawler.dto;

import com.zhikao.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 爬虫任务分页查询参数。
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class CrawlTaskQueryDTO extends PageQuery {

    /** 按来源过滤，可空 */
    private Long sourceId;

    /** 按状态过滤：PENDING|RUNNING|SUCCESS|FAILED，可空 */
    private String status;
}
