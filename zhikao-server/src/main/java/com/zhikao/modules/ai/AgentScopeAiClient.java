package com.zhikao.modules.ai;

import com.zhikao.common.BusinessException;
import com.zhikao.config.AiProperties;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.ModelRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 AgentScope（ReActAgent）的大模型调用客户端 —— 单轮 prompt 调用通道。
 *
 * 用途：智能出题 / 辅助阅卷 / 错题分析这三个「一问一答」场景。
 * 需要工具调用、多轮对话、流式的场景走 {@code modules.ai.agent} 下的智能体通道。
 *
 * <b>并发修正（二期）</b>：AgentScope 官方注释写得很明确——
 * 「ReActAgent is not thread-safe」，所以**不能**把它缓存在字段里跨请求复用。
 * 一期用的是 volatile 懒加载单例 Agent，单机压测下可能出现上下文串台。
 * 现在改为：缓存 {@link Model}（HTTP 客户端，构建开销大且可复用），
 * 每次调用新建 {@link ReActAgent}（轻量对象）。
 */
@Component
public class AgentScopeAiClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(AgentScopeAiClient.class);

    /** 统一的角色设定：约束输出风格，避免大段寒暄 */
    private static final String BASE_SYSTEM_PROMPT =
            "你是「智考在线」教学系统的助手。回答要简洁、准确、结构化，直接给出可用内容，不要寒暄。";

    private final AiProperties properties;

    /** 缓存的模型客户端（可复用） */
    private volatile Model model;

    public AgentScopeAiClient(AiProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean available() {
        return properties.ready();
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        if (!available()) {
            throw new BusinessException("AI 能力未启用：请在后端配置 zhikao.ai.enabled=true 与 zhikao.ai.api-key 后重试");
        }
        String prompt = (systemPrompt == null || systemPrompt.isBlank())
                ? userPrompt
                : systemPrompt + "\n\n" + userPrompt;
        try {
            Duration timeout = Duration.ofSeconds(properties.getTimeoutSeconds());
            // 每次调用新建 Agent：ReActAgent 非线程安全，复用会出现调用间状态串台
            ReActAgent agent = ReActAgent.builder()
                    .name("zhikao-ai")
                    .sysPrompt(BASE_SYSTEM_PROMPT)
                    .model(model())
                    .build();
            Msg reply = agent.call(prompt, RuntimeContext.empty()).block(timeout);
            return reply == null ? "" : reply.getTextContent();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("调用 AgentScope 失败：{}", e.getMessage());
            throw new BusinessException("AI 调用失败：" + e.getMessage());
        }
    }

    /** 懒加载并复用模型客户端：api-key 未配置时不在启动期报错 */
    private Model model() {
        Model current = model;
        if (current == null) {
            synchronized (this) {
                if (model == null) {
                    ModelCreationContext context = ModelCreationContext.builder()
                            .apiKey(properties.getApiKey())
                            .baseUrl(properties.getBaseUrl())
                            .build();
                    model = ModelRegistry.resolve(properties.getModel(), context);
                }
                current = model;
            }
        }
        return current;
    }
}
