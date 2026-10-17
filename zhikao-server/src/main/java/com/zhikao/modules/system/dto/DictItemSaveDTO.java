package com.zhikao.modules.system.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 字典项保存参数（id 为空即新增）。
 */
@Data
public class DictItemSaveDTO {

    private Long id;

    @NotBlank(message = "字典类型不能为空")
    private String dictType;

    @NotBlank(message = "展示文案不能为空")
    private String itemLabel;

    @NotBlank(message = "取值不能为空")
    private String itemValue;

    private String color;

    private Integer sort;

    private Integer status;
}
