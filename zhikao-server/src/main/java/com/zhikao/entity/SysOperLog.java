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
 * 操作日志（审计）。
 *
 * 只记录「写操作 + 登录登出」，请求参数经脱敏与截断后落库；
 * 写入走异步有界线程池，队列满时丢弃并告警，避免审计拖慢业务响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_oper_log")
public class SysOperLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务模块，如「用户管理」 */
    private String module;

    /** 操作动作，如「新增用户」 */
    private String action;

    /** HTTP 方法 */
    private String method;

    /** 请求路径 */
    private String requestUri;

    /** 操作人账号 */
    private String operName;

    /** 操作人角色 */
    private String operRole;

    /** 来源 IP */
    private String operIp;

    /** 请求参数摘要（已脱敏、已截断） */
    private String params;

    /** 1成功 0失败 */
    private Integer resultStatus;

    /** 失败原因 */
    private String errorMsg;

    /** 耗时（毫秒） */
    private Long costMs;

    private LocalDateTime operTime;
}
