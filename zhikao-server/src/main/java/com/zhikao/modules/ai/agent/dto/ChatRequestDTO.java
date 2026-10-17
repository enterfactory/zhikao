package com.zhikao.modules.ai.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 对话请求。
 */
@Data
public class ChatRequestDTO {

    /** 会话 id；为空表示新建会话 */
    private Long conversationId;

    /** Agent 类型：coordinator / question / mark / analytics / assistant；为空按 coordinator */
    private String agentType;

    @NotBlank(message = "请输入内容")
    @Size(max = 2000, message = "单次输入不要超过 2000 字")
    private String message;
}
