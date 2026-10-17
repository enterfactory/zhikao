package com.zhikao.config;

import com.zhikao.common.LoginUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求链路号过滤器。
 *
 * 解决的问题：一次请求会写多行日志（业务日志、慢 SQL、异常堆栈），
 * 没有链路号时只能靠时间戳猜哪几行属于同一次请求；有了它，一行 grep 就能串起来。
 *
 * 实现要点：
 * 1. 优先透传上游传来的 {@code X-Trace-Id}（网关 / 前端已生成时保持全链路一致）；
 * 2. 写入 MDC，日志格式里用 {@code %X{traceId}} 输出；
 * 3. 同时写回响应头，前端报错时可以把 traceId 一起贴给后端；
 * 4. {@code finally} 里必须清理 MDC——线程池复用线程时残留会串号。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 请求头 / 响应头名称 */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** MDC 中的 key，与 application.yml 的日志格式保持一致 */
    public static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        // 提前写响应头：即使后续抛异常，前端也能拿到链路号
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 容器线程会被复用：不清 MDC 会串链路号，不清身份快照会串用户
            MDC.remove(TRACE_ID_MDC_KEY);
            LoginUtil.clear();
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String inbound = request.getHeader(TRACE_ID_HEADER);
        if (inbound != null && !inbound.isBlank() && inbound.length() <= 64) {
            return inbound;
        }
        // 取 UUID 前 16 位：可读性与唯一性足够，日志里不占位置
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
