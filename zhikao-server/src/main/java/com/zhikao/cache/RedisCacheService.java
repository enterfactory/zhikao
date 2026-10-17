package com.zhikao.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

/**
 * 共享状态的 Redis 实现。
 *
 * <p><b>为什么令牌桶必须用 Lua：</b>
 * 「读桶 → 计算 → 写桶」在客户端做会产生竞态：两个并发请求同时读到 1 个令牌，
 * 各自扣减后写回 0，结果是两个请求都放行了。这种 bug 只在并发下出现，
 * 单测和本地点两下永远复现不了。Lua 脚本在 Redis 内单线程执行，天然原子。
 *
 * <p><b>降级不是「可选装饰」：</b>
 * 本项目承诺「不配 Redis 也能跑」，所以每次操作失败都会回落到
 * {@link MemoryCacheService}，并且 30 秒后尝试恢复——Redis 重启回来应用层能自动接上，
 * 不需要改任何配置、也不需要重启应用。
 *
 * <p><b>注意：</b>这里用的是 application 自己的 Redis 连接（{@code spring.data.redis}），
 * 与业务缓存共用同一个实例，不额外维护一套连接。
 */
public class RedisCacheService implements CacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);

    /**
     * 令牌桶 Lua：一次调用完成「按时间补令牌 → 尝试扣减 → 写回并续期」。
     * ARGV: [1]桶容量 [2]每秒补充数 [3]当前毫秒时间戳
     */
    private static final String TOKEN_BUCKET_LUA = """
            local capacity = tonumber(ARGV[1])
            local refill = tonumber(ARGV[2])
            local nowMs = tonumber(ARGV[3])
            local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
            local tokens = tonumber(bucket[1])
            local ts = tonumber(bucket[2])
            if tokens == nil or ts == nil then
                tokens = capacity
                ts = nowMs
            end
            local elapsed = (nowMs - ts) / 1000.0
            if elapsed > 0 then
                tokens = math.min(capacity, tokens + elapsed * refill)
            end
            local allowed = 0
            if tokens >= 1.0 then
                tokens = tokens - 1.0
                allowed = 1
            end
            redis.call('HSET', KEYS[1], 'tokens', string.format('%.6f', tokens),
                    'ts', string.format('%.0f', nowMs))
            local ttlMs = 3600000
            if refill > 0 then
                ttlMs = math.ceil(capacity / refill) * 1000 + 60000
            end
            redis.call('PEXPIRE', KEYS[1], ttlMs)
            return allowed
            """;

    /** 自增：只有首次写入时设置过期时间，避免每次调用都把窗口往后推 */
    private static final String INCREMENT_LUA = """
            local n = redis.call('INCR', KEYS[1])
            if n == 1 then
                redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[1]))
            end
            return n
            """;

    /** 自减：不允许扣成负数（未读数可能因为重复点击而多减） */
    private static final String DECREMENT_LUA = """
            local v = redis.call('DECR', KEYS[1])
            if v < 0 then
                redis.call('SET', KEYS[1], 0)
                v = 0
            end
            return v
            """;

    private static final RedisScript<Long> TOKEN_BUCKET =
            new DefaultRedisScript<>(TOKEN_BUCKET_LUA, Long.class);
    private static final RedisScript<Long> INCREMENT =
            new DefaultRedisScript<>(INCREMENT_LUA, Long.class);
    private static final RedisScript<Long> DECREMENT =
            new DefaultRedisScript<>(DECREMENT_LUA, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisConnectionFactory connectionFactory;
    private final boolean required;
    private final MemoryCacheService fallback = new MemoryCacheService();

    /** Redis 是否可用。运行期失败会被置为 false，恢复探测成功后重新置回 true */
    private volatile boolean alive;
    private volatile long lastProbeAt = System.currentTimeMillis();

    /** 恢复探测间隔：降级后每隔这么久试一次心跳，Redis 回来了能自动接上 */
    private static final long RETRY_INTERVAL_MS = 30_000L;

    public RedisCacheService(StringRedisTemplate redisTemplate, RedisConnectionFactory connectionFactory) {
        this(redisTemplate, connectionFactory, false);
    }
    public RedisCacheService(StringRedisTemplate redisTemplate, RedisConnectionFactory connectionFactory, boolean required) {
        this.required = required;
        this.redisTemplate = redisTemplate;
        this.connectionFactory = connectionFactory;
        this.alive = probe();
        if(required && !alive) throw new IllegalStateException("ECS Redis 连接失败，拒绝降级启动");
    }

    /** 启动时探测一次 Redis 是否真的可用，决定走 Redis 还是回落内存 */
    private boolean probe() {
        try {
            String pong;
            try(var connection = connectionFactory.getConnection()) { pong = connection.ping(); }
            boolean ok = pong != null;
            if (ok) {
                log.info("Redis 可用，共享状态（限流 / 登录失败计数 / AI 配额 / 通知未读）将存入 Redis");
            }
            return ok;
        } catch (Exception e) {
            log.warn("Redis 不可用（{}），共享状态回落到内存实现；" +
                    "单实例下功能完全正常，多实例部署请先启动 Redis", e.getMessage());
            return false;
        }
    }

    /** 每次操作的入口：不可用则走内存，并且定期探测 Redis 是否恢复 */
    private boolean useRedis() {
        if (alive) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lastProbeAt > RETRY_INTERVAL_MS) {
            lastProbeAt = now;
            boolean recovered = probe();
            if (recovered) {
                alive = true;
                log.info("Redis 已恢复，共享状态重新回到 Redis");
            }
            return recovered;
        }
        if(required) throw new IllegalStateException("Redis 暂不可用，请稍后重试");
        return false;
    }

    @Override
    public String mode() {
        return alive ? "redis" : "memory";
    }

    @Override
    public boolean redisAlive() {
        return alive;
    }

    @Override
    public boolean tryAcquire(String key, int capacity, double refillPerSecond) {
        if (!useRedis()) {
            return fallback.tryAcquire(key, capacity, refillPerSecond);
        }
        try {
            Long allowed = redisTemplate.execute(TOKEN_BUCKET,
                    List.of(KEY_PREFIX + key),
                    String.valueOf(capacity), String.valueOf(refillPerSecond),
                    String.valueOf(System.currentTimeMillis()));
            return allowed != null && allowed == 1L;
        } catch (Exception e) {
            degrade(e);
            return fallback.tryAcquire(key, capacity, refillPerSecond);
        }
    }

    @Override
    public long increment(String key, Duration ttl) {
        if (!useRedis()) {
            return fallback.increment(key, ttl);
        }
        try {
            Long value = redisTemplate.execute(INCREMENT, List.of(KEY_PREFIX + key),
                    String.valueOf(ttl.toMillis()));
            return value == null ? 0L : value;
        } catch (Exception e) {
            degrade(e);
            return fallback.increment(key, ttl);
        }
    }

    @Override
    public long decrement(String key) {
        if (!useRedis()) {
            return fallback.decrement(key);
        }
        try {
            Long value = redisTemplate.execute(DECREMENT, List.of(KEY_PREFIX + key));
            return value == null ? 0L : value;
        } catch (Exception e) {
            degrade(e);
            return fallback.decrement(key);
        }
    }

    @Override
    public long counter(String key) {
        if (!useRedis()) {
            return fallback.counter(key);
        }
        try {
            String value = redisTemplate.opsForValue().get(KEY_PREFIX + key);
            return value == null ? 0L : Long.parseLong(value);
        } catch (Exception e) {
            degrade(e);
            return fallback.counter(key);
        }
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        if (!useRedis()) {
            fallback.set(key, value, ttl);
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + key, value, ttl);
        } catch (Exception e) {
            degrade(e);
            fallback.set(key, value, ttl);
        }
    }

    @Override
    public String get(String key) {
        if (!useRedis()) {
            return fallback.get(key);
        }
        try {
            return redisTemplate.opsForValue().get(KEY_PREFIX + key);
        } catch (Exception e) {
            degrade(e);
            return fallback.get(key);
        }
    }

    @Override
    public void delete(String key) {
        if (!useRedis()) {
            fallback.delete(key);
            return;
        }
        try {
            redisTemplate.delete(KEY_PREFIX + key);
        } catch (Exception e) {
            degrade(e);
            fallback.delete(key);
        }
    }

    /** 降级：只打一次警告，避免 Redis 抖动时把日志刷爆 */
    private synchronized void degrade(Exception e) {
        if (alive) {
            log.warn("Redis 操作失败，本次回落到内存实现：{}", e.getMessage());
        }
        alive = false;
        lastProbeAt = System.currentTimeMillis();
        if(required) throw new IllegalStateException("Redis 操作失败，请稍后重试", e);
    }

    /**
     * 清理回落目标里的过期数据。
     *
     * <p>内存回落理论上只是临时状态，但如果 Redis 一直没回来，
     * 里面的 Map 会一直增长；由容器周期调用
     * {@link MemoryCacheService#cleanup()} 兜住这个口子。
     */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 10 * 60 * 1000L)
    public void cleanup() {
        fallback.cleanup();
    }
}
