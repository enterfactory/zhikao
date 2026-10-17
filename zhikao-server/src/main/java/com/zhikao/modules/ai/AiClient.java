package com.zhikao.modules.ai;

/**
 * 大模型调用客户端抽象。
 * 业务层只依赖本接口，便于在测试环境替换为桩实现，也便于后续更换底层框架。
 */
public interface AiClient {

    /** 是否具备调用条件（未配置 API Key 时为 false） */
    boolean available();

    /**
     * 发起一次对话补全。
     *
     * @param systemPrompt 角色设定，可为空
     * @param userPrompt   用户输入
     * @return 模型返回的文本
     */
    String chat(String systemPrompt, String userPrompt);
}
