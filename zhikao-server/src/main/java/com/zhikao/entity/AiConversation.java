package com.zhikao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 会话（多轮对话的持久化载体，服务重启不丢）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_conversation")
public class AiConversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属用户 */
    private Long userId;

    /** 会话标题（取首条提问前若干字） */
    private String title;

    /** 使用的 Agent 类型 */
    private String agentType;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
