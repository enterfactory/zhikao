package com.zhikao.modules.ai.agent.vo;

import lombok.Data;

/**
 * 一个可用的 Agent 入口（前端「智能助手」页的能力清单）。
 *
 * <p>为什么由后端下发而不是前端写死：Agent 的可见性与可用工具数都由角色决定，
 * 前端写死就会出现「学生界面里有个点不开的出题专家」这类错配。
 * 后端算好再给，前端只负责渲染。
 */
@Data
public class AgentCatalogVO {

    /** coordinator / question / mark / analytics / assistant */
    private String code;

    private String label;

    private String description;

    /** 是否专家（可被主控委派）；主控自己为 false */
    private Boolean specialist;

    /** 该角色通过这个 Agent 实际可调用的工具数量（用于向用户说明能力边界） */
    private Integer toolCount;
}
