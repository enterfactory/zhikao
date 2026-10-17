package com.zhikao.modules.ai.agent.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 同步对话的返回结果（SSE 不可用时的兜底）。
 */
@Data
public class ChatReplyVO {

    private Long conversationId;

    private Long messageId;

    private String content;

    private List<ToolCallVO> toolCalls = new ArrayList<>();

    private Integer tokenUsage = 0;

    private String agentType;

    /** 是否由真实模型生成（未配置模型时为 false，前端据此提示「非 AI 生成」） */
    private boolean aiGenerated = true;
}
