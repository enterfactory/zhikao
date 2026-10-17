package com.zhikao.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 字典类型保存参数（id 为空即新增）。
 */
@Data
public class DictTypeSaveDTO {

    private Long id;

    @NotBlank(message = "字典名称不能为空")
    private String dictName;

    @NotBlank(message = "字典类型不能为空")
    @Pattern(regexp = "^[a-z][a-z0-9_]{1,49}$",
            message = "字典类型只能由小写字母、数字与下划线组成，且以字母开头")
    private String dictType;

    private String remark;

    private Integer status;
}
