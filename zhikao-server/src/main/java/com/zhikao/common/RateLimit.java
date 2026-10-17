package com.zhikao.common;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解：由 {@link RateLimitAspect} 切入，用内存令牌桶保护重点接口。
 *
 * 保护对象（二期）：登录（防爆破）、交卷（防并发冲击）、AI 调用（控成本）。
 *
 * @see TokenBucketRateLimiter
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 限流维度前缀，如 "login"、"submit"；实际 key 会拼上用户 ID 或 IP */
    String key();

    /** 桶容量：允许的突发次数 */
    int capacity() default 10;

    /** 每秒补充令牌数：稳态允许的调用频率 */
    double refillPerSecond() default 1.0;

    /** 限流时的提示 */
    String message() default "操作过于频繁，请稍后再试";
}
