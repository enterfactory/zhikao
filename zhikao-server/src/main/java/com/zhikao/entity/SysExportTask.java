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
 * 异步导出任务。
 *
 * <p>为什么需要一张任务表：导出改成异步后，请求线程立即返回，
 * 前端必须有一个「可追问的东西」——否则用户不知道该等多久、也不知道是失败还是慢。
 * 用数据库存任务状态而不是内存 Map，是因为进程重启后内存里的任务就凭空消失了，
 * 用户会一直轮询一个永远不完成的任务。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_export_task")
public class SysExportTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 业务类型：exam-grade 等 */
    private String bizType;

    /** 业务主键，如考试 id */
    private Long bizId;

    /** 生成后的下载名 */
    private String fileName;

    /** 0生成中 1成功 2失败 */
    private Integer status;

    /** 生成完成后关联的 sys_file.id */
    private Long fileId;

    private String errorMsg;

    private String createBy;

    private LocalDateTime createTime;

    private LocalDateTime finishTime;
}
