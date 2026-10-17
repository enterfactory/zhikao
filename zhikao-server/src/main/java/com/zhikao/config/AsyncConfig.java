package com.zhikao.config;

import com.zhikao.common.LoginUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置。
 *
 * <p><b>取舍一：操作日志写入用有界队列，而不是默认的无界队列。</b>
 * 无界队列在日志堆积时会吃光内存（这是生产事故的常见成因）；
 * 有界队列 + CallerRunsPolicy 让压力回传给调用方，宁可慢一点，也不让内存爆掉。
 *
 * <p><b>取舍二：用 {@link TaskDecorator} 把「线程本地上下文」带进异步线程。</b>
 * 链路号（MDC）与登录身份快照都存在 ThreadLocal 里，直接丢给线程池会全部丢失，
 * 表现为「异步日志没有 traceId」以及「异步代码取登录态报
 * SaTokenContext 上下文尚未初始化」。装饰器在提交时抓取、在任务开始时绑定、
 * 在任务结束时清理——这也是线程池复用场景下唯一不会串号的做法。
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /** 操作日志专用线程池：小、有界、可观测 */
    public static final String OPER_LOG_EXECUTOR = "operLogExecutor";

    /**
     * 导出任务线程池：与操作日志分开，避免一次大导出把日志写入的线程占满
     * （两者混用时最典型的症状是「导出期间操作日志少了一截」）。
     */
    public static final String EXPORT_EXECUTOR = "exportExecutor";

    /**
     * 采集流水线线程池。
     *
     * <p><b>为什么必须独立出来</b>：{@link #getAsyncExecutor()} 返回的是操作日志池，
     * 而流水线跑在默认 {@code @Async} 上——等于「一份卷子 OCR 十分钟」和「写一条日志」
     * 抢同一批 2~4 个线程，采集期间操作日志会静默积压。
     *
     * <p><b>为什么这里用 AbortPolicy 而不是 CallerRunsPolicy</b>：一次文档采集是分钟级的
     * （扫描页 OCR 尤其），队列满时让提交线程自己跑，就是把整条流水线压到 HTTP 请求线程上——
     * 表现为前端请求超时、Tomcat 工作线程被长期占用，一个用户的批量上传拖慢整站。
     * 宁可当场拒绝并回一句「采集繁忙，请稍后重试」。
     *
     * <p>并发数没有放大：离线 OCR 引擎内部是串行锁（原生层非并发安全），
     * 真正的吞吐上限在识别层而不在线程数，这里放大只会让任务在锁上排队、占着线程不放。
     */
    public static final String CRAWL_EXECUTOR = "crawlExecutor";

    @Bean(CRAWL_EXECUTOR)
    public Executor crawlExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(64);              // 吸收一次批量上传的突发
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("crawl-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        // 关闭前等在跑的采集：采到一半被进程退出掐掉，原文与待审会留下不一致的半成品
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    @Bean(EXPORT_EXECUTOR)
    public Executor exportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(120);
        executor.setThreadNamePrefix("export-");
        // 拒绝仅影响本次调度，已持久化的任务由恢复器重新投递。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean(OPER_LOG_EXECUTOR)
    public Executor operLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(1000);          // 有界队列：满则触发拒绝策略
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("oper-log-");
        // 队列满时由提交线程自己执行（不丢日志，代价是业务线程多花一次插入的时间）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 把 traceId 与登录身份快照带进异步线程（否则异步日志无链路号、取登录态会报上下文未初始化）
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    /**
     * 上下文传播装饰器：提交任务时抓取，执行任务时绑定，执行完清理。
     * 抓取动作发生在**调用方线程**（也就是请求线程），因此能拿到真实的登录态。
     */
    private static final class ContextPropagatingTaskDecorator implements TaskDecorator {

        @Override
        public Runnable decorate(Runnable runnable) {
            String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
            LoginUtil.Snapshot snapshot = LoginUtil.capture();
            return () -> {
                try {
                    if (traceId != null) {
                        MDC.put(TraceIdFilter.TRACE_ID_MDC_KEY, traceId);
                    }
                    LoginUtil.bind(snapshot);
                    runnable.run();
                } finally {
                    // 线程池复用线程，必须清理，否则下一个任务会读到上一个请求的身份
                    MDC.remove(TraceIdFilter.TRACE_ID_MDC_KEY);
                    LoginUtil.clear();
                }
            };
        }
    }

    @Override
    public Executor getAsyncExecutor() {
        return operLogExecutor();
    }

    /** 异步任务里的异常不会冒泡到业务线程，这里兜底记录，避免「日志静默失败」 */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) ->
                log.warn("异步任务执行失败 [{}]：{}", method.getName(), throwable.getMessage());
    }
}
