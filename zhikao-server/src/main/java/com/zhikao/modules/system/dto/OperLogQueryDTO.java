package com.zhikao.modules.system.dto;

import com.zhikao.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 操作日志查询条件。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OperLogQueryDTO extends PageQuery {

    /** 模块模糊匹配 */
    private String module;

    /** 操作人账号模糊匹配 */
    private String operName;

    /** 1成功 0失败；不传查全部 */
    private Integer resultStatus;

    /** 起止时间（含） */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
