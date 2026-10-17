package com.zhikao.modules.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 班级列表视图：只出对外业务字段，不外放内部审计列。
 * 控制器直出实体会把库表结构当契约，后续加一列内部审计字段就自动漏到前端。
 */
@Data
public class ClassVO {

    private Long id;

    private String className;

    /** 年级，如 2024级 */
    private String grade;

    private String major;

    private LocalDateTime createTime;
}
