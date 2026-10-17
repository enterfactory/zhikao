package com.zhikao.cache;

import java.time.Duration;

/**
 * 共享状态存储。
 *
 * <p><b>它解决的是「多实例」问题，不是「慢」问题。</b>
 * 单实例下存在 JVM 内存里的三样东西（限流令牌桶、登录失败计数、AI 配额计数）
 * 在起了第二个实例后不是变慢，而是<b>变错</b>：同一个用户交替打到两个实例，
 * 限流阈值实际翻倍、失败计数被绕过、配额被重复计算。
 * 所以把它们挪到一个所有实例都能看见的地方。
 *
 * <p><b>它不接管什么：</b>交卷幂等、批改乐观锁、改分留痕、防作弊证据、考试状态流转。
 * 这些有事务语义，真相源必须是数据库——见 {@code Redis引入方案.md} 中的迁移边界。
 *
 * <p><b>降级约定：</b>实现类必须在 Redis 不可用（没配、没起、中途挂了）时
 * 自动回落到内存语义，并且在日志里说清楚当前用的是哪一种。
 * 这条不是"锦上添花"，而是为了保住「克隆下来不用装 Redis 也能跑」的承诺——
 * 一个 Pull Request 之后还要先装 Redis 才能本地验证的项目，最后一定没人验证。
 */
public interface CacheService {

    /** 全部 key 的统一前缀，避免与其它应用共用 Redis 时互相覆盖 */
    String KEY_PREFIX = "zhikao:";

    /**
     * 当前生效的存储模式：{@code redis} 或 {@code memory}。
     * 直接暴露给运维接口，方便部署后一眼确认「到底有没有用上 Redis」——
     * 比让人去猜配置有没有生效可靠得多。
     */
    String mode();

    /** Redis 是否真的可用（启动时探测 + 运行期失败降级的综合结果） */
    boolean redisAlive();

    /**
     * 令牌桶限流：尝试取走一个令牌。
     *
     * @param key              限流维度（已含用户/IP 标识）
     * @param capacity         桶容量，允许的突发次数
     * @param refillPerSecond  每秒补充令牌数，即稳态 QPS 上限
     * @return true 放行；false 限流
     */
    boolean tryAcquire(String key, int capacity, double refillPerSecond);

    /**
     * 计数器自增并在首次写入时设置过期时间（一次原子操作）。
     *
     * <p>用于 AI 每日配额与登录失败计数：这类数据的共同特点是
     * 「过了窗口就该自动消失」，用 TTL 代替定时任务去做清理，比手动回收更不容易漏。
     *
     * @return 自增后的值
     */
    long increment(String key, Duration ttl);

    /** 计数器自减，主要用于「已读通知」从未读数里扣掉 */
    long decrement(String key);

    /** 读取计数器当前值，不存在返回 0 */
    long counter(String key);

    /** 写入带过期时间的字符串 */
    void set(String key, String value, Duration ttl);

    /** 读取字符串，不存在返回 null */
    String get(String key);

    /** 删除 */
    void delete(String key);
}
