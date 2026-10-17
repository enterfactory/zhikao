package com.zhikao.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / OpenAPI 配置：提供接口文档，访问 http://localhost:8080/doc.html。
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("智考在线 API")
                        .description("在线考试与自动阅卷系统接口文档")
                        .version("v1.0"));
    }
}
