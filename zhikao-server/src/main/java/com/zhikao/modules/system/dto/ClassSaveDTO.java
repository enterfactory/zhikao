package com.zhikao.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 班级新增 / 修改参数。
 */
@Data
public class ClassSaveDTO {

    /** 主键：为空表示新增，不为空表示修改 */
    private Long id;

    @NotBlank(message = "班级名称不能为空")
    @Size(max = 50, message = "班级名称长度不能超过 50")
    private String className;

    @Size(max = 20, message = "年级长度不能超过 20")
    private String grade;

    @Size(max = 50, message = "专业长度不能超过 50")
    private String major;
}
