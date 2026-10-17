package com.zhikao.common;

/**
 * 学生在某场考试中的参与状态。
 * 待考 → 考试中 → 已交卷 → 已阅卷（全部主观题批改完成后进入已阅卷）。
 */
public enum ExamStudentStatusEnum {

    WAITING(0, "待考"),
    RUNNING(1, "考试中"),
    SUBMITTED(2, "已交卷"),
    MARKED(3, "已阅卷");

    private final int code;
    private final String label;

    ExamStudentStatusEnum(int code, String label) {
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
    public static ExamStudentStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ExamStudentStatusEnum status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }

    /** 按编码取中文名 */
    public static String labelOf(Integer code) {
        ExamStudentStatusEnum status = of(code);
        return status == null ? "未知" : status.label;
    }
}
