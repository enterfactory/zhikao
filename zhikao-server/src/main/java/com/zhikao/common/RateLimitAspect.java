package com.zhikao.common;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 限流切面：把 {@link RateLimit} 注解翻译成对 {@link TokenBucketRateLimiter} 的一次调用。
 *
 * 维度选择逻辑：
 * - 已登录 → 按「维度前缀 + 用户 ID」限流（同一人的行为聚合，避免 NAT 后互相影响）；
 * - 未登录（如登录接口本身）→ 按「维度前缀 + 来源 IP」限流（此时 IP 是唯一可用的身份）。
 */
@Aspect
@Component
public class RateLimitAspect {

    private final TokenBucketRateLimiter rateLimiter;

    public RateLimitAspect(TokenBucketRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        String key = rateLimit.key() + ":" + identify();
        if (!rateLimiter.tryAcquire(key, rateLimit.capacity(), rateLimit.refillPerSecond())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, rateLimit.message());
        }
        return joinPoint.proceed();
    }

    /** 取限流主体：优先当前登录用户，其次来源 IP */
    private String identify() {
        try {
            return "u" + LoginUtil.userId();
        } catch (Exception ignored) {
            // 未登录属于登录接口的正常路径，继续按 IP 限流
        }
        return "ip" + WebUtil.clientIp();
    }
}
