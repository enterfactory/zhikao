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
 * 爬虫任务（六期）：一次「某个来源跑一遍 fetch→parse→clean→tag 流水线」的执行记录。
 *
 * <p>fetched/cleaned/staged 三个计数构成一条漏斗，供面板观测：
 * 抓到多少条原文、清洗后剩多少条、其中打标完成待审多少条。
 * 失败时把异常摘要写进 error，RUNNING→SUCCESS/FAILED 由异步线程收尾。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("crawl_task")
public class CrawlTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属来源 crawl_source.id */
    private Long sourceId;

    private String leaseOwner;
    private LocalDateTime leaseUntil;
    private Integer attempts;

    /** 状态：PENDING|RUNNING|SUCCESS|FAILED */
    private String status;

    /** 抓到并落 crawl_raw 的条数 */
    private Integer fetched;

    /** 清洗通过、落 crawl_question 的条数 */
    private Integer cleaned;

    /** 已打标待审（TAGGED 阶段）条数 */
    private Integer staged;

    /** 失败原因摘要 */
    private String error;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
