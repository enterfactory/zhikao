package com.zhikao.common;

import com.zhikao.config.SecurityProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 密码强度策略。
 *
 * 规则（可配置）：长度 ≥ 8，且同时包含字母与数字。
 * 说明：不做「必须含特殊字符」这类过度约束——它会显著提高弱密码的书写成本，
 * 却挡不住「Password1!」这种典型弱口令，收益低于代价。
 */
@Component
public class PasswordPolicy {

    private final SecurityProperties security;

    public PasswordPolicy(SecurityProperties security) {
        this.security = security;
    }

    /** 校验密码强度，不合规直接抛业务异常 */
    public void validate(String rawPassword) {
        if (!StringUtils.hasText(rawPassword)) {
            throw new BusinessException("密码不能为空");
        }
        if (rawPassword.length() < security.getPasswordMinLength()) {
            throw new BusinessException("密码长度至少 " + security.getPasswordMinLength() + " 位");
        }
        if (security.isPasswordRequireMixed()) {
            boolean hasLetter = rawPassword.chars().anyMatch(Character::isLetter);
            boolean hasDigit = rawPassword.chars().anyMatch(Character::isDigit);
            if (!hasLetter || !hasDigit) {
                throw new BusinessException("密码必须同时包含字母与数字");
            }
        }
    }
}
