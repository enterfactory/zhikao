package com.zhikao.modules.ai.agent.vo;

import lombok.Data;

/**
 * 一次工具调用（可解释性的最小单元）。
 * 前端把它折叠展示为「已调用：get_knowledge_stats / 已返回摘要」，
 * 让用户看得见 Agent 是怎么得出结论的。
 *
 * 说明：AgentScope 的 ToolCallStartEvent 只暴露工具名，不暴露参数，
 * 因此这里不设 args 字段——留一个永远为空的字段比不留更容易误导人。
 */
@Data
public class ToolCallVO {

    /** 工具名 */
    private String name;

    /** started（开始调用）/ finished（已返回） */
    private String state;

    /** 结果摘要（截断后的工具返回内容，用于解释「结论从哪来」） */
    private String resultSummary;
}
