package com.zhikao.modules.ai.agent.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话摘要（左侧会话列表）。
 */
@Data
public class ConversationVO {

    private Long id;

    private String title;

    private String agentType;

    private String agentLabel;

    private long messageCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
