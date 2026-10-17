package com.zhikao.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile({"ecs","postgres","prod","local"})
@RequiredArgsConstructor
public class EcsEnvironmentGuard {
    private final Environment environment;
    @PostConstruct
    void validate(){
        if(environment.getProperty("zhikao.demo-data.enabled",Boolean.class,false))
            throw new IllegalStateException("ECS/生产模式禁止演示数据初始化");
        if(!environment.getProperty("zhikao.redis.required",Boolean.class,false))
            throw new IllegalStateException("ECS/生产模式必须使用共享 Redis");
        if(!environment.getProperty("zhikao.security.default-password","").isBlank())
            throw new IllegalStateException("ECS/生产模式禁止共享默认密码");
        String origins=environment.getProperty("zhikao.cors.allowed-origin-patterns","");
        if(origins.isBlank() || origins.contains("*"))
            throw new IllegalStateException("ECS/生产模式需要明确的 CORS 来源白名单");
        if(!environment.getProperty("spring.datasource.url","").startsWith("jdbc:postgresql:"))
            throw new IllegalStateException("ECS/生产模式只支持 PostgreSQL");
    }
}
