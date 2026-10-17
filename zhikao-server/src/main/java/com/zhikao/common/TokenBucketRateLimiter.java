package com.zhikao.common;

import com.zhikao.cache.CacheService;
import com.zhikao.cache.MemoryCacheService;
import org.springframework.stereotype.Component;

/**
 * 令牌桶限流器：现在只是一个门面，真正的桶由 {@link CacheService} 决定落在哪。
 *
 * <p><b>为什么保留这一层：</b>
 * 限流的调用方只有一个切面，直接换成 CacheService 确实更简单；但保留这个语义化入口，
 * 让「这里是按令牌桶限流」这件事在代码里看得见，也让既有的单测可以继续用内存语义验证算法。
 *
 * <p><b>存储位置的历史：</b>
 * 这个类原来是纯实现，桶放在 {@code ConcurrentHashMap} 里。单实例下完全够用，
 * 但一旦起了第二个实例，同一个用户交替打两台机器会得到两份独立的桶——
 * 配置的 5 次实际变成最多 10 次。这不是"性能不够"，是<b>规则失效</b>，
 * 所以把桶挪到了所有实例都能看见的地方（Redis），本机没 Redis 时自动回落内存，语义不变。
 */
@Component
public class TokenBucketRateLimiter {

    private final CacheService cacheService;

    /** 生产使用的构造：由 Spring 注入共享存储 */
    public TokenBucketRateLimiter(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    /**
     * 无参构造：使用内存实现，供单元测试验证令牌桶算法本身（不涉及 Redis）。
     */
    public TokenBucketRateLimiter() {
        this(new MemoryCacheService());
    }

    /**
     * 尝试获取一个令牌。
     *
     * @param key             限流维度，如 "submit:u12"、"login:ip127.0.0.1"
     * @param capacity        桶容量（允许的突发次数）
     * @param refillPerSecond 每秒补充的令牌数（稳态 QPS 上限）
     * @return true 放行；false 限流
     */
    public boolean tryAcquire(String key, int capacity, double refillPerSecond) {
        return cacheService.tryAcquire(key, capacity, refillPerSecond);
    }
}
