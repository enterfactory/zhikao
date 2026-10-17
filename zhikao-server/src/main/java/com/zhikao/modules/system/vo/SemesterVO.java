package com.zhikao.modules.system.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 学期视图：下拉选项与列表回显共用。
 */
@Data
public class SemesterVO {

    private Long id;

    private String name;

    private String schoolYear;

    private Integer termNo;

    private LocalDate startDate;

    private LocalDate endDate;

    /** 1当前学期 */
    private Integer isCurrent;
}
