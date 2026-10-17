package com.zhikao.config;

import com.zhikao.cache.CacheService;
import com.zhikao.cache.MemoryCacheService;
import com.zhikao.cache.RedisCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis 接入的装配入口。
 *
 * <p><b>一条明确的开关：{@code zhikao.redis.enabled}。</b>
 * 关掉它时连 Redis 连接都不会去尝试——这决定了本项目是否还能维持
 * 「克隆下来不用装任何东西就能跑」的承诺。默认给 true 是因为 Redis 是部署标配，
 * 但同时保证连不上时自动回落（见 {@link RedisCacheService}）。
 *
 * <p><b>会话为什么要迁进来：</b>
 * 单实例下位于 JVM 内存的三样东西（会话、限流令牌桶、登录失败计数）在起了第二个实例后
 * 不是变慢而是<b>变错</b>。除此之外还有个更日常的好处：发版重启不会把所有人踢下线。
 *
 * <p><b>什么坚决不迁：</b>
 * 交卷幂等、批改乐观锁、改分留痕、防作弊证据、考试状态流转。
 * 它们带有事务语义，真相源只能是数据库——用 Redis 锁去保护一个数据库事务，
 * 等于引入了一个与事务不同步的第二个真相源。
 */
@Configuration
public class CacheConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CacheConfiguration.class);

    @Value("${zhikao.redis.enabled:true}")
    private boolean redisEnabled;
    @Value("${zhikao.redis.required:false}")
    private boolean required;
    @Value("${zhikao.redis.namespace:zhikao:local:}")
    private String namespace;

    /**
     * 共享状态存储：能用 Redis 就用，不能用就回落内存，对上层完全透明。
     *
     * <p>用 {@link ObjectProvider} 而不是直接注入，是因为当 Redis starter 被排除时
     * {@code StringRedisTemplate} 这个 bean 根本不存在，直接注入会在启动阶段抛
     * NoSuchBeanDefinitionException——一个「禁用 Redis」的配置不该导致启动失败。
     */
    @Bean
    public CacheService cacheService(ObjectProvider<StringRedisTemplate> templateProvider,
                                     ObjectProvider<RedisConnectionFactory> factoryProvider) {
        if (!redisEnabled) {
            log.info("Redis 已通过 zhikao.redis.enabled=false 关闭，共享状态使用内存实现");
            return new MemoryCacheService();
        }
        StringRedisTemplate template = templateProvider.getIfAvailable();
        RedisConnectionFactory factory = factoryProvider.getIfAvailable();
        if (template == null || factory == null) {
            if(required) throw new IllegalStateException("当前环境要求真实 Redis");
            log.warn("未检测到 Redis 连接工厂，共享状态回落内存实现");
            return new MemoryCacheService();
        }
        template.setKeySerializer(new com.zhikao.cache.NamespacedKeySerializer(namespace));
        return new RedisCacheService(template, factory, required);
    }

    /**
     * 把 Sa-Token 的会话存储切到 Redis。
     *
     * <p>做成 {@link ApplicationRunner} 而不是静态代码块，是因为它依赖
     * Redis 连接工厂已经就绪；这个时机虽然晚于 Web 容器启动，
     * 但此时还没有任何真实请求进来，不存在「一会记忆体一会 Redis」的撕裂窗口。
     */
    @Component
    public static class SaTokenDaoInitializer implements ApplicationRunner {

        private final CacheService cacheService;
        private final ObjectProvider<RedisConnectionFactory> factoryProvider;
        @Value("${zhikao.redis.namespace:zhikao:local:}")
        private String namespace;
        @Value("${zhikao.redis.required:false}")
        private boolean required;

        public SaTokenDaoInitializer(CacheService cacheService,
                                     ObjectProvider<RedisConnectionFactory> factoryProvider) {
            this.cacheService = cacheService;
            this.factoryProvider = factoryProvider;
        }

        @Override
        public void run(ApplicationArguments args) {
            if (!cacheService.redisAlive()) {
                if(required) throw new IllegalStateException("Redis 会话存储不可用");
                // The Redis plugin auto-registers a DAO even when our optional switch is off.
                cn.dev33.satoken.SaManager.setSaTokenDao(new cn.dev33.satoken.dao.SaTokenDaoDefaultImpl());
                log.info("Redis 不可用：Sa-Token 会话保持在内存中（单实例可用，重启会掉登录态）");
                return;
            }
            RedisConnectionFactory factory = factoryProvider.getIfAvailable();
            if (factory == null) {
                cn.dev33.satoken.SaManager.setSaTokenDao(new cn.dev33.satoken.dao.SaTokenDaoDefaultImpl());
                return;
            }
            try {
                cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate dao =
                        new cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate();
                dao.init(factory);
                dao.stringRedisTemplate.setKeySerializer(new com.zhikao.cache.NamespacedKeySerializer(namespace));
                cn.dev33.satoken.SaManager.setSaTokenDao(dao);
                log.info("Sa-Token 会话已迁移到 Redis：支持多实例部署，重启应用不再强制下线");
            } catch (Exception e) {
                if(required) throw new IllegalStateException("Redis 会话初始化失败", e);
                cn.dev33.satoken.SaManager.setSaTokenDao(new cn.dev33.satoken.dao.SaTokenDaoDefaultImpl());
                // 会话切换失败不该阻止应用启动，回落内存至少还能用
                log.warn("Sa-Token 会话迁移 Redis 失败，继续使用内存实现：{}", e.getMessage());
            }
        }
    }
}
