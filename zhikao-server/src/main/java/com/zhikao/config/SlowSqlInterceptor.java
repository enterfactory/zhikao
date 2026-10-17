package com.zhikao.config;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;

/**
 * 慢 SQL 观测（MyBatis 拦截器）。
 *
 * 为什么需要它：数据库是「先压榨再加中间件」策略里的那个「先压榨」，
 * 而压榨的前提是**看得见**——没有慢 SQL 记录，加索引就只能靠猜。
 *
 * 只记录、不干预：拦截器不改变任何执行行为（返回原结果），
 * 因此可以放心在生产开启；阈值可配，默认 300ms。
 */
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
public class SlowSqlInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(SlowSqlInterceptor.class);

    /** 慢 SQL 阈值（毫秒） */
    @Value("${zhikao.slow-sql-threshold-ms:300}")
    private long thresholdMs = 300;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return invocation.proceed();
        } finally {
            long cost = System.currentTimeMillis() - start;
            if (cost >= thresholdMs) {
                logSlowSql(invocation, cost);
            }
        }
    }

    private void logSlowSql(Invocation invocation, long cost) {
        try {
            MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
            Object parameter = invocation.getArgs().length > 1 ? invocation.getArgs()[1] : null;
            BoundSql boundSql = statement.getBoundSql(parameter);
            // SQL 压缩成单行，避免日志被多行 SQL 冲散；日志按行统计没问题
            String sql = boundSql.getSql().replaceAll("\\s+", " ").trim();
            log.warn("慢 SQL 耗时 {}ms | mapper={} | sql={}", cost, statement.getId(), sql);
        } catch (Exception e) {
            log.warn("慢 SQL 记录失败：{}", e.getMessage());
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 阈值通过 Spring 配置注入，此处无需额外属性
    }
}
