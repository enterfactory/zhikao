package com.zhikao.modules.exam.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作弊证据视图：教师看到的是「谁、第几次、什么时候、在第几题」。
 */
@Data
public class CheatLogVO {

    private Long id;

    private Long examStudentId;

    private String studentName;

    private String cheatType;

    /** 本次是该答卷的第几次异常 */
    private Integer seq;

    private LocalDateTime happenTime;

    /** 发生时的题号，可为空 */
    private Integer sort;

    private String remark;
}
