package com.zhikao.modules.ai.agent.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话消息。
 */
@Data
public class ChatMessageVO {

    private Long id;

    /** user / assistant */
    private String role;

    private String content;

    /** 本轮的工具调用轨迹（仅 assistant 消息有） */
    private java.util.List<ToolCallVO> toolCalls;

    private Integer tokenUsage;

    private LocalDateTime createTime;
}
