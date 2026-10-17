package com.zhikao.modules.portal.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 练习断点上报参数（POST /portal/resume，按当前登录用户 upsert）。
 */
@Data
public class ResumeSaveDTO {

    /** 科目 id，可空（自定义练习集没有科目归属时） */
    private Long subjectId;

    @NotBlank(message = "科目名称不能为空")
    private String subjectName;

    /** 套题名，可空（自主练习无固定套名时） */
    private String setName;

    @NotNull(message = "已做题数不能为空")
    @Min(value = 0, message = "已做题数不能为负")
    private Integer done;

    @NotNull(message = "总题数不能为空")
    @Min(value = 0, message = "总题数不能为负")
    private Integer total;

    /** 正确率（百分比数值，0-100），可空 */
    @DecimalMin(value = "0", message = "正确率不能为负")
    @DecimalMax(value = "100", message = "正确率不能超过 100")
    private BigDecimal accuracy;
}
