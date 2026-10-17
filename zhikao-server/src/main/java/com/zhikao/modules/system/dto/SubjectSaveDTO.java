package com.zhikao.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 科目新增 / 修改参数。
 */
@Data
public class SubjectSaveDTO {

    /** 主键：为空表示新增，不为空表示修改 */
    private Long id;

    @NotBlank(message = "科目名称不能为空")
    @Size(max = 50, message = "科目名称长度不能超过 50")
    private String subjectName;

    @Size(max = 30, message = "科目编码长度不能超过 30")
    private String subjectCode;
}
