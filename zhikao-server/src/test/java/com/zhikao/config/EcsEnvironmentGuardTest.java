package com.zhikao.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class EcsEnvironmentGuardTest {
    @Test void productionRejectsDemoSharedDefaultMemorySessionsAndWildcardCors(){
        var env=new MockEnvironment().withProperty("spring.datasource.url","jdbc:postgresql://example/db")
                .withProperty("zhikao.demo-data.enabled","false").withProperty("zhikao.redis.required","true")
                .withProperty("zhikao.security.default-password","").withProperty("zhikao.cors.allowed-origin-patterns","https://exam.example.org");
        var guard=new EcsEnvironmentGuard(env);
        assertDoesNotThrow(guard::validate);
        for(String key:new String[]{"zhikao.demo-data.enabled","zhikao.redis.required","zhikao.security.default-password","zhikao.cors.allowed-origin-patterns","spring.datasource.url"}){
            String original=env.getProperty(key);
            String invalid=switch(key){
                case "zhikao.demo-data.enabled" -> "true";
                case "zhikao.redis.required" -> "false";
                case "zhikao.security.default-password" -> "123456";
                case "zhikao.cors.allowed-origin-patterns" -> "*";
                default -> "jdbc:h2:mem:forbidden";
            };
            env.setProperty(key,invalid);assertThrows(IllegalStateException.class,guard::validate,key);env.setProperty(key,original);
        }
    }
}
