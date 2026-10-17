package com.zhikao.modules.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 科目列表视图：create_by 属内部审计列不外放（列表展示只需要名称与编码）。
 */
@Data
public class SubjectVO {

    private Long id;

    private String subjectName;

    /** 科目编码，如 JAVA */
    private String subjectCode;

    private LocalDateTime createTime;
}
