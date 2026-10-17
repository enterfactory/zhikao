package com.zhikao.common;

import com.zhikao.config.SecurityProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 安全加固相关的单元测试：密码策略、错误码契约、令牌桶限流。
 */
@DisplayName("安全加固")
class SecurityGuardTest {

    @Test
    @DisplayName("密码策略：长度与字母数字组合校验")
    void passwordPolicy() {
        SecurityProperties properties = new SecurityProperties();
        properties.setPasswordMinLength(8);
        properties.setPasswordRequireMixed(true);
        PasswordPolicy policy = new PasswordPolicy(properties);

        assertThrows(BusinessException.class, () -> policy.validate(""));
        assertThrows(BusinessException.class, () -> policy.validate(null));
        assertThrows(BusinessException.class, () -> policy.validate("abc123"), "长度不足应被拒绝");
        assertThrows(BusinessException.class, () -> policy.validate("abcdefgh"), "纯字母应被拒绝");
        assertThrows(BusinessException.class, () -> policy.validate("12345678"), "纯数字应被拒绝");
        assertDoesNotThrow(() -> policy.validate("zhikao2026"));
        assertDoesNotThrow(() -> policy.validate("a1234567"));
    }

    @Test
    @DisplayName("错误码契约：业务码与鉴权码分段固定")
    void errorCodeContract() {
        assertEquals(200, ErrorCode.SUCCESS.getCode());
        assertEquals(40001, ErrorCode.PARAM_ERROR.getCode());
        assertEquals(40101, ErrorCode.UNAUTHORIZED.getCode());
        assertEquals(40301, ErrorCode.FORBIDDEN.getCode());
        assertEquals(42901, ErrorCode.RATE_LIMITED.getCode());
        assertEquals(42902, ErrorCode.ACCOUNT_LOCKED.getCode());
        assertEquals(50001, ErrorCode.BUSINESS_ERROR.getCode());
        assertEquals(50000, ErrorCode.SYSTEM_ERROR.getCode());

        assertEquals(50001, new BusinessException("业务失败").getCode());
        assertEquals(40301, new BusinessException(ErrorCode.FORBIDDEN, "无权限").getCode());
    }

    @Test
    @DisplayName("令牌桶：突发容量用尽后拒绝")
    void tokenBucket() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter();
        String key = "test:user";

        // 补充速率取极小值：确保测试期间没有令牌被补充回来，使断言与时序无关
        for (int i = 0; i < 3; i++) {
            assertEquals(true, limiter.tryAcquire(key, 3, 0.0001), "第 " + (i + 1) + " 次应放行");
        }
        assertEquals(false, limiter.tryAcquire(key, 3, 0.0001), "超出容量应被限流");
    }

    @Test
    @DisplayName("令牌桶：按 key 隔离，互不影响")
    void tokenBucketIsolation() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter();
        assertEquals(true, limiter.tryAcquire("a", 1, 0.0001));
        assertEquals(false, limiter.tryAcquire("a", 1, 0.0001));
        // 另一个 key 有自己的桶
        assertEquals(true, limiter.tryAcquire("b", 1, 0.0001));
    }
}
