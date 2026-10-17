package com.zhikao.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token Web 配置：注册登录拦截器，对需要认证的接口做鉴权；
 * 放行登录、接口文档等无需认证的路径。
 *
 * 注：类名刻意避开 SaTokenConfig，以免与 Sa-Token 自动配置的同名 bean 冲突。
 */
@Configuration
@lombok.RequiredArgsConstructor
public class SaTokenWebConfig implements WebMvcConfigurer {
    private final com.zhikao.mapper.SysUserMapper users;


    /** 白名单：无需登录即可访问的路径。
     *  注：已配置 server.servlet.context-path=/api，Spring 拦截器匹配的是
     *  去除上下文路径后的 handler 路径（如 /auth/login），故此处不加 /api 前缀。
     *  Actuator 只放行 health/info：metrics 等运行指标仍需登录，避免对外暴露内部状态。 */
    private static final String[] EXCLUDE_PATHS = {
            "/auth/login",
            "/doc.html",
            "/v3/api-docs/**",
            "/swagger-resources/**",
            "/webjars/**",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info"
    };

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()) {
                    @Override
                    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                                             Object handler) throws Exception {
                        // Browsers send preflight without credentials; actual requests remain authenticated.
                        if (CorsUtils.isPreFlightRequest(request)) return true;
                        boolean allowed = super.preHandle(request, response, handler);
                        var user = users.selectById(StpUtil.getLoginIdAsLong());
                        if(user == null || !Integer.valueOf(1).equals(user.getStatus())) {
                            StpUtil.logout();
                            throw new com.zhikao.common.BusinessException(40101, "账号已停用，请重新登录");
                        }
                        Object version=StpUtil.getTokenSession().get("authVersion");
                        if(version == null || !String.valueOf(user.getAuthVersion()).equals(String.valueOf(version))) {
                            StpUtil.logout();
                            throw new com.zhikao.common.BusinessException(40101,"账号授权已更新，请重新登录");
                        }
                        String path=request.getRequestURI().substring(request.getContextPath().length());
                        if(Integer.valueOf(1).equals(user.getPasswordChangeRequired())) {
                            if(user.getPasswordExpiresAt()==null || java.time.LocalDateTime.now().isAfter(user.getPasswordExpiresAt())) {
                                StpUtil.logout();
                                throw new com.zhikao.common.BusinessException(40101,"初始口令已过期，请联系管理员");
                            }
                            if(!java.util.Set.of("/auth/info","/auth/logout","/auth/password/change").contains(path))
                                throw new com.zhikao.common.BusinessException(40302,"请先修改初始口令");
                        }
                        return allowed;
                    }
                })
                .addPathPatterns("/**")
                .excludePathPatterns(EXCLUDE_PATHS);
    }
}
