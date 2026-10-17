package com.zhikao.modules.dashboard.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 趋势图上的一个点：既用于「开考场次」（total），也用于「平均分」（avgValue）。
 * 同一个结构承载两条序列，前端一张图两条线就能画出来，不必为两种形状各写一个 DTO。
 *
 * 字段命名说明：{@code statDay} / {@code avgValue} 刻意避开 {@code day} / {@code value}——
 * 这两个词在 H2 中是保留字，直接用作 SQL 别名会报「expected identifier」。
 * 与其为兼容性在每个查询上挂 @Results 映射，不如让列名与属性名保持一致，靠下划线转驼峰自动映射。
 */
@Data
public class TrendPointVO {

    /** 统计日期 */
    private LocalDate statDay;

    /** 计数型指标：如当日开考场次 */
    private Long total;

    /** 数值型指标：如当日平均分 */
    private BigDecimal avgValue;
}
