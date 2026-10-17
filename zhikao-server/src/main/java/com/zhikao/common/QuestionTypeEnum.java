package com.zhikao.common;

/**
 * 题型枚举：与 question.type 对应，并区分客观题 / 主观题。
 * 集中定义可避免「判分逻辑 / 页面展示 / Excel 导入解析」三处各写一份映射。
 */
public enum QuestionTypeEnum {

    SINGLE(1, "单选", true),
    MULTIPLE(2, "多选", true),
    JUDGE(3, "判断", true),
    BLANK(4, "填空", true),
    /** 简答为主观题：交卷后需教师批改，不参与自动判分 */
    ESSAY(5, "简答", false);

    private final int code;
    private final String label;
    private final boolean objective;

    QuestionTypeEnum(int code, String label, boolean objective) {
        this.code = code;
        this.label = label;
        this.objective = objective;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public boolean isObjective() {
        return objective;
    }

    /** 按编码取枚举，未匹配返回 null */
    public static QuestionTypeEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (QuestionTypeEnum type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }

    /** 按编码取中文名 */
    public static String labelOf(Integer code) {
        QuestionTypeEnum type = of(code);
        return type == null ? "未知" : type.label;
    }

    /** 是否客观题（未知类型按主观题处理，避免误判分） */
    public static boolean isObjective(Integer code) {
        QuestionTypeEnum type = of(code);
        return type != null && type.objective;
    }

    /** 解析 Excel 中的「题型」单元格，兼容 单选/single/1 等写法 */
    public static Integer parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();
        for (QuestionTypeEnum type : values()) {
            if (value.equals(type.label) || value.equalsIgnoreCase(type.name())) {
                return type.code;
            }
        }
        try {
            int code = Integer.parseInt(value);
            return of(code) == null ? null : code;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
