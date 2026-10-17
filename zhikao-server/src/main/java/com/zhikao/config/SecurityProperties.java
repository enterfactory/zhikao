package com.zhikao.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 安全加固配置（二期）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "zhikao.security")
public class SecurityProperties {

    /** 密码最小长度 */
    private int passwordMinLength = 8;

    /** Explicitly configured only in local demo profile; production has no shared default. */
    private String defaultPassword = "";

    /** 密码是否必须同时包含字母与数字 */
    private boolean passwordRequireMixed = true;

    /** 同账号连续失败多少次后锁定 */
    private int loginMaxFailPerAccount = 5;

    /** 同 IP 连续失败多少次后限制 */
    private int loginMaxFailPerIp = 20;

    /** 锁定时长（分钟） */
    private int loginLockMinutes = 15;

    /** AI 每用户每日调用上限（控成本，作为未配置角色额度时的兜底值） */
    private int aiDailyLimitPerUser = 200;

    /**
     * AI 按角色的每日调用上限。
     *
     * <p>为什么按角色而不是统一一个数：三类角色的调用目的完全不同——
     * 教师是出题/阅卷（单次调用贵，但一天用不了几次），
     * 学生是问答练习（单次便宜，但可能刷一整天），
     * 管理员是排查与导出。用同一个上限，要么把学生卡死，要么对教师形同虚设。
     *
     * <p>级别：学生 &lt; 教师 &lt; 管理员，与数据可见范围同向收紧。
     */
    private Map<String, Integer> aiDailyLimitByRole = new LinkedHashMap<>();

    {
        aiDailyLimitByRole.put("student", 30);
        aiDailyLimitByRole.put("teacher", 100);
        aiDailyLimitByRole.put("admin", 300);
    }

    /** 某角色的每日调用上限；未配置时退回每用户上限 */
    public int aiDailyLimitFor(String role) {
        if (role != null && aiDailyLimitByRole.containsKey(role)) {
            return aiDailyLimitByRole.get(role);
        }
        return aiDailyLimitPerUser;
    }
}
