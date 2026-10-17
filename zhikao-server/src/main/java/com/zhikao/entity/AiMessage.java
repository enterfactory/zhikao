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
 * AI 会话消息。
 * tool_calls 保存本轮的工具调用轨迹（工具名 + 参数 + 结果摘要），
 * 供前端折叠展示「Agent 是怎么得出这个结论的」——这是可解释性的落点。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_message")
public class AiMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    /** user / assistant / tool */
    private String role;

    private String content;

    /** 工具调用轨迹 JSON */
    private String toolCalls;

    private Integer tokenUsage;

    private LocalDateTime createTime;
}
