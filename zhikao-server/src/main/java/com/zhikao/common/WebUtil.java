package com.zhikao.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Web 相关工具：从当前请求上下文取信息。
 * 取 IP 的逻辑集中在这里，避免操作日志、限流、登录守卫各写一份导致口径不一致。
 */
public final class WebUtil {

    private WebUtil() {
    }

    /** 当前请求，非 Web 上下文（如定时任务）返回 null */
    public static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }

    /**
     * 取客户端 IP：优先代理透传头，其次 remoteAddr。
     * 只取 X-Forwarded-For 的第一段（最靠近客户端的一跳）。
     */
    public static String clientIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return "unknown";
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return comma > 0 ? forwarded.substring(0, comma).trim() : forwarded.trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}
