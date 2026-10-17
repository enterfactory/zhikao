package com.zhikao.common;

/**
 * 考试状态：由考试时间窗自动推导，不需要人工维护。
 * 未开始（now < startTime）→ 进行中（时间窗内）→ 已结束（now > endTime）。
 */
public enum ExamStatusEnum {

    NOT_STARTED(0, "未开始"),
    RUNNING(1, "进行中"),
    FINISHED(2, "已结束");

    private final int code;
    private final String label;

    ExamStatusEnum(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 按编码取枚举，未匹配返回 null */
    public static ExamStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ExamStatusEnum status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    /** 按编码取中文名 */
    public static String labelOf(Integer code) {
        ExamStatusEnum status = of(code);
        return status == null ? "未知" : status.label;
    }
}
