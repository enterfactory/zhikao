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
 * AI 任务记录：保存每次调用大模型的入参与结果，便于回溯与治理。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_task")
public class AiTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务类型：gen_question 智能出题 / assist_mark 辅助阅卷 / analysis 错题分析 / chat 智能对话 */
    private String type;

    /** 发起调用的用户 id（用于成本统计与「每用户每日上限」的持久化核算） */
    private Long userId;

    /** 关联业务 id（如考试 id、题目 id、会话 id） */
    private Long refId;

    private String prompt;

    private String result;

    /** 0待处理 1成功 2失败 */
    private Integer status;

    /**
     * 发起角色（admin / teacher / student）。
     * 按角色配额与审计都要用：学生和教师不该有一样调用额度，
     * 出问题时也要能先按角色筛一遍。
     */
    private String roleCode;

    /** 使用的 Agent：coordinator / question / mark / analytics / assistant */
    private String agentType;

    /** 工具调用序列 JSON —— 用于回溯「这个结论是怎么查出来的」 */
    private String toolCalls;

    /** 本次调用消耗的 token 数（模型未回传时为 0） */
    private Integer tokenUsage;

    /** 本次调用耗时（毫秒）：慢调用是靠这个字段发现的 */
    private Long costMs;

    /** 失败原因；成功时为空。没有它，「AI 答错了」只能靠用户描述 */
    private String errorMsg;

    private LocalDateTime createTime;
}
