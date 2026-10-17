package com.zhikao.modules.ai.vo;

import lombok.Data;

/**
 * 纯文本型 AI 结果（智能出题、分析报告通用）。
 */
@Data
public class AiTextVO {

    /** 模型返回的正文 */
    private String content;

    /** 是否由大模型真实生成（false 表示未配置模型，本次未产生内容） */
    private boolean aiGenerated;
}
