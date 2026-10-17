package com.zhikao.modules.ai.agent;

import com.zhikao.common.LoginUtil;
import com.zhikao.common.UserTypeEnum;

/**
 * 单次对话的身份上下文。
 *
 * <b>为什么必须有这个类</b>：AgentScope 的调用链跑在 Reactor 线程上，
 * 而 Sa-Token 的登录态存在 ThreadLocal 里。一旦在工具方法里直接调
 * {@code StpUtil.getLoginIdAsLong()}，在模型调用的线程上会取不到值——
 * 表现是「工具莫名其妙说未登录」，而且只在多线程/真实模型下才复现。
 *
 * 解法：在**请求线程**上把身份快照下来，随工具实例一起传给 Agent。
 * 既躲开 ThreadLocal 的线程边界问题，也天然做到「工具实例 = 一次请求」的隔离。
 */
public final class AgentContext {

    private final Long userId;

    private final String username;

    private final String realName;

    /** admin / teacher / student */
    private final String role;

    private AgentContext(Long userId, String username, String realName, String role) {
        this.userId = userId;
        this.username = username;
        this.realName = realName;
        this.role = role;
    }

    /** 在当前（请求）线程上快照登录身份 */
    public static AgentContext current() {
        String role = LoginUtil.roleCode();
        Long userId;
        try {
            userId = LoginUtil.userId();
        } catch (Exception e) {
            // 兜底：理论上 Controller 层已保证登录，这里不让异常冒到工具调用里
            userId = null;
        }
        return new AgentContext(userId, LoginUtil.username(), "", role);
    }

    /** 手工构造（测试与子 Agent 复用父上下文时使用） */
    public static AgentContext of(Long userId, String username, String realName, String role) {
        return new AgentContext(userId, username, realName, role);
    }

    /** 补齐真实姓名（登录会话里没有姓名，需查一次库） */
    public AgentContext withRealName(String name) {
        return new AgentContext(userId, username, name, role);
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getRealName() {
        return realName;
    }

    public String getRole() {
        return role;
    }

    public boolean isAdmin() {
        return UserTypeEnum.ADMIN.getRole().equals(role);
    }

    public boolean isTeacher() {
        return UserTypeEnum.TEACHER.getRole().equals(role);
    }

    public boolean isStudent() {
        return UserTypeEnum.STUDENT.getRole().equals(role);
    }

    /** 是否教师或管理员（管理侧只读工具的可见范围） */
    public boolean isStaff() {
        return isAdmin() || isTeacher();
    }

    /** 角色中文名，用于提示词 */
    public String roleLabel() {
        return switch (role) {
            case "admin" -> "系统管理员";
            case "teacher" -> "教师";
            case "student" -> "学生";
            default -> "用户";
        };
    }

    /** 拼进系统提示词的身份说明：让模型知道「我现在代表谁在说话」 */
    public String describe() {
        String name = realName == null || realName.isBlank() ? username : realName;
        return "当前用户：" + name + "（账号 " + username + "，角色 " + roleLabel() + "）";
    }
}
