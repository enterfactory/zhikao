package com.zhikao.modules.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix="zhikao.vector")
public class VectorProperties {
    private boolean enabled;
    private String baseUrl;
    private String apiKey;
    private String collection="zhikao_knowledge_v1";
    private String embeddingBaseUrl;
    private String embeddingApiKey;
    private String embeddingModel;
    private int dimensions=1024;
}
