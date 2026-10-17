package com.zhikao.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 共享状态的内存实现。
 *
 * <p>单实例下它与 Redis 实现<b>语义完全等价</b>，所以它是 Redis 不可用时的回落目标，
 * 也是本机不装 Redis 时的正常工作模式。
 *
 * <p>实现上有两处刻意为之：
 * <ol>
 *   <li>令牌桶用「懒填充」而不是定时补充——只在真正取令牌时按流逝时间补，
 *       省掉一个调度线程，也让「空闲很久的桶」天然地被回收；</li>
 *   <li>计数器自身携带过期时间戳，读取时惰性判过期，配合定时清扫防止内存无限增长。
 *       这与 Redis 的 TTL 语义对齐：两边都不需要额外的清理任务。</li>
 * </ol>
 */
public class MemoryCacheService implements CacheService {

    private final java.time.Clock clock;
    public MemoryCacheService(){this(java.time.Clock.systemUTC());}
    public MemoryCacheService(java.time.Clock clock){this.clock=clock;}
    private static final Logger log = LoggerFactory.getLogger(MemoryCacheService.class);

    /** 令牌桶：key -> 桶 */
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /** 计数器：key -> 计数 */
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    /** 带过期时间的字符串：key -> 值 */
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public String mode() {
        return "memory";
    }

    @Override
    public boolean redisAlive() {
        return false;
    }

    @Override
    public boolean tryAcquire(String key, int capacity, double refillPerSecond) {
        return buckets.computeIfAbsent(KEY_PREFIX + key, k -> new Bucket(capacity))
                .tryAcquire(capacity, refillPerSecond);
    }

    @Override
    public long increment(String key, Duration ttl) {
        long[] result = new long[1];
        counters.compute(KEY_PREFIX + key, (k, current) -> {
            Counter counter = current == null || current.expired(clock.millis()) ? new Counter(ttl,clock.millis()) : current;
            result[0] = counter.increment();
            return counter;
        });
        return result[0];
    }

    @Override
    public long decrement(String key) {
        Counter counter = counters.get(KEY_PREFIX + key);
        if (counter == null) {
            return 0;
        }
        long value = counter.decrement();
        if (value <= 0) {
            counters.remove(KEY_PREFIX + key);
        }
        return Math.max(value, 0);
    }

    @Override
    public long counter(String key) {
        Counter counter = counters.get(KEY_PREFIX + key);
        if (counter == null || counter.expired(clock.millis())) {
            return 0;
        }
        return counter.value();
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        entries.put(KEY_PREFIX + key, new Entry(value, System.currentTimeMillis() + ttl.toMillis()));
    }

    @Override
    public String get(String key) {
        Entry entry = entries.get(KEY_PREFIX + key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            entries.remove(KEY_PREFIX + key);
            return null;
        }
        return entry.value();
    }

    @Override
    public void delete(String key) {
        String full = KEY_PREFIX + key;
        entries.remove(full);
        counters.remove(full);
        buckets.remove(full);
    }

    /** 周期性清理：过期条目与长时间未访问的令牌桶 */
    @Scheduled(fixedDelay = 10 * 60 * 1000L)
    public void cleanup() {
        long now = System.currentTimeMillis();
        int beforeEntries = entries.size();
        entries.entrySet().removeIf(e -> e.getValue().isExpired());
        int beforeBuckets = buckets.size();
        buckets.entrySet().removeIf(e -> e.getValue().idleTooLong(now));
        counters.entrySet().removeIf(e -> e.getValue().expired(clock.millis()));
        log.debug("内存缓存清理：条目 {}/{}，令牌桶 {}/{}",
                beforeEntries - entries.size(), beforeEntries,
                beforeBuckets - buckets.size(), beforeBuckets);
    }

    /** 令牌桶 */
    private static final class Bucket {

        private double tokens;
        private long lastRefillAt;
        private volatile long lastAccess;

        private Bucket(int capacity) {
            this.tokens = capacity;
            this.lastRefillAt = System.currentTimeMillis();
            this.lastAccess = lastRefillAt;
        }

        private synchronized boolean tryAcquire(int capacity, double refillPerSecond) {
            long now = System.currentTimeMillis();
            lastAccess = now;
            double elapsedSeconds = (now - lastRefillAt) / 1000.0;
            if (elapsedSeconds > 0) {
                tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
                lastRefillAt = now;
            }
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private boolean idleTooLong(long now) {
            return lastAccess < now - 30 * 60 * 1000L;
        }
    }

    /** 带 TTL 的计数器 */
    private static final class Counter {

        private long value;
        private final long expireAt;

        private Counter(Duration ttl,long now) {
            this.expireAt = now + ttl.toMillis();
        }

        private synchronized long increment() {
            return ++value;
        }

        private synchronized long decrement() {
            return --value;
        }

        private synchronized long value() {
            return value;
        }

        private synchronized boolean expired(long now) {
            return now >= expireAt;
        }
    }

    /** 带 TTL 的字符串条目 */
    private record Entry(String value, long expireAt) {

        private boolean isExpired() {
            return System.currentTimeMillis() >= expireAt;
        }
    }
}
