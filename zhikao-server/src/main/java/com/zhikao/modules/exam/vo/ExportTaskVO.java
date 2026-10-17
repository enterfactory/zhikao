package com.zhikao.modules.exam.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 异步导出任务视图：前端据此轮询状态并在完成后拿到下载地址。
 */
@Data
public class ExportTaskVO {

    private Long id;

    /** 0生成中 1成功 2失败 */
    private Integer status;

    private String statusName;

    private String fileName;

    /** 生成完成后关联的文件 id */
    private Long fileId;

    /** 可直接拼在网关后的下载地址 */
    private String downloadUrl;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime finishTime;
}
