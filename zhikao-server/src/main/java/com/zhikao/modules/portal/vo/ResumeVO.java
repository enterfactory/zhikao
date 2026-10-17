package com.zhikao.modules.portal.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 「继续练习」断点（对外契约：subjectName,setName,done,total,accuracy,lastAt）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeVO {

    private String subjectName;

    /** 套题名 */
    private String setName;

    private Integer done;

    private Integer total;

    /** 正确率（百分比数值，86.50 = 86.5%） */
    private BigDecimal accuracy;

    /** 最后练习时间 */
    private LocalDateTime lastAt;
}
