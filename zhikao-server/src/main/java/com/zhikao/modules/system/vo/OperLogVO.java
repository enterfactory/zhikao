package com.zhikao.modules.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志返回结构。
 */
@Data
public class OperLogVO {

    private Long id;

    private String module;

    private String action;

    private String method;

    private String requestUri;

    private String operName;

    private String operRole;

    /** 角色中文名，便于页面直接展示 */
    private String operRoleName;

    private String operIp;

    private String params;

    private Integer resultStatus;

    private String errorMsg;

    private Long costMs;

    private LocalDateTime operTime;
}
