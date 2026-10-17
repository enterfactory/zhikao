package com.zhikao.modules.ai.agent.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能助手的「能力面板」：当前角色能用哪些 Agent、边界在哪。
 *
 * <p>把边界一并下发（{@link #scope}）是有意的：用户最常问的是
 * 「为什么我不能用这个」，界面上一句话讲清楚比让模型每次解释一遍更省事。
 */
@Data
public class ChatCatalogVO {

    /** admin / teacher / student */
    private String role;

    /** 角色中文名 */
    private String roleLabel;

    /** 该角色的能力与边界说明（一句话，可直接展示） */
    private String scope;

    /** 可用的 Agent 列表 */
    private List<AgentCatalogVO> agents = new ArrayList<>();

    /** 今日剩余调用次数：让用户知道自己还有多少次，而不是超限后才知道没了 */
    private Integer quotaRemaining;

    /** 今日额度上限 */
    private Integer quotaLimit;
}
