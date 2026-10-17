package com.zhikao.modules.dashboard.vo;

import lombok.Data;

/**
 * 首页指标卡。
 */
@Data
public class MetricVO {

    /** 指标 key，前端用于选图标 / 配色 */
    private String key;

    private String label;

    /** 展示值（已格式化为字符串，避免前端再判类型） */
    private String value;

    /** 单位，如「场」「条」 */
    private String unit;

    /** 一句话说明，鼠标悬停显示 */
    private String hint;

    /** 语义色：primary / success / warning / danger */
    private String color;
}
