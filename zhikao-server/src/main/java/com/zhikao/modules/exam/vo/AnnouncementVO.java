package com.zhikao.modules.exam.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 考试公告视图：对外字段即实体业务列，create_by 属内部审计列不外放
 * （公告的展示语义只有「内容 + 时间」，学生侧不需要作者账号信息）。
 */
@Data
public class AnnouncementVO {

    private Long id;

    private Long examId;

    private String content;

    private LocalDateTime createTime;
}
