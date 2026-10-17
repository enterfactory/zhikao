package com.zhikao.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 增强配置。
 *
 * 说明：项目未内置任何大模型凭据，必须由使用者自行配置 api-key 后 AI 能力才可用。
 * 未配置时相关接口会返回明确的「未配置」提示，不会伪造任何结果。
 */
@Data
@Component
@ConfigurationProperties(prefix = "zhikao.ai")
public class AiProperties {

    /** 是否启用 AI 能力 */
    private boolean enabled = false;

    /** 大模型 API Key（OpenAI 兼容服务，如 DeepSeek / 通义千问） */
    private String apiKey = "";

    /** 服务地址（OpenAI 兼容的 /v1 前缀） */
    private String baseUrl = "https://api.deepseek.com/v1";

    /** 模型标识，格式为「提供方:模型名」，交由 AgentScope 的 ModelRegistry 解析 */
    private String model = "deepseek:deepseek-chat";

    /** 单次调用超时（秒） */
    private int timeoutSeconds = 60;

    /** 是否具备真实调用条件 */
    public boolean ready() {
        return enabled && apiKey != null && !apiKey.isBlank();
    }
}
