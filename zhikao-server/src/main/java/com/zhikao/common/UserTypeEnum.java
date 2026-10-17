package com.zhikao.common;

/**
 * 用户类型枚举：与 sys_user.user_type 对应，同时承载 Sa-Token 的角色标识。
 * 集中定义可避免「角色判断 / 中文名展示 / Excel 导入解析」三处各写一份映射。
 */
public enum UserTypeEnum {

    ADMIN(1, "admin", "系统管理员", "管理员"),
    TEACHER(2, "teacher", "教师", "教师"),
    STUDENT(3, "student", "学生", "学生");

    /** 与 sys_user.user_type 对应的编码 */
    private final int code;

    /** Sa-Token 角色标识（admin / teacher / student） */
    private final String role;

    /** 页面展示名称 */
    private final String label;

    /** Excel 导入时可接受的别名（除此之外还支持 role 标识与编码本身） */
    private final String alias;

    UserTypeEnum(int code, String role, String label, String alias) {
        this.code = code;
        this.role = role;
        this.label = label;
        this.alias = alias;
    }

    public int getCode() {
        return code;
    }

    public String getRole() {
        return role;
    }

    public String getLabel() {
        return label;
    }

    /** 按编码取枚举，未匹配返回 null */
    public static UserTypeEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (UserTypeEnum type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }

    /** 按编码取角色标识，未知类型按「学生」处理（权限最小化） */
    public static String roleOf(Integer code) {
        UserTypeEnum type = of(code);
        return type == null ? STUDENT.role : type.role;
    }

    /** 按编码取展示名称 */
    public static String labelOf(Integer code) {
        UserTypeEnum type = of(code);
        return type == null ? "未知" : type.label;
    }

    /**
     * 解析 Excel 中的「角色」单元格，兼容 管理员/教师/学生、admin/teacher/student、1/2/3 三种写法。
     *
     * @return user_type 编码；无法识别时返回 null
     */
    public static Integer parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();
        for (UserTypeEnum type : values()) {
            if (value.equals(type.label) || value.equalsIgnoreCase(type.role) || value.equals(type.alias)) {
                return type.code;
            }
        }
        try {
            return of(Integer.valueOf(value)) == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
