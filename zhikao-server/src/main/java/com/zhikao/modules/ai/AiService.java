package com.zhikao.modules.ai;

import com.zhikao.modules.ai.dto.AiAssistMarkDTO;
import com.zhikao.modules.ai.dto.AiGenQuestionDTO;
import com.zhikao.modules.ai.vo.AiAnalysisVO;
import com.zhikao.modules.ai.vo.AiGenQuestionVO;
import com.zhikao.modules.ai.vo.AiMarkSuggestionVO;

/**
 * AI 增强服务（可选能力）。
 *
 * 定位：轻量调用现成的大模型 API 做教学辅助，不涉及模型训练；
 * 所有 AI 结果都只是「建议」，不会自动写入题目库或覆盖成绩。
 */
public interface AiService {

    /**
     * 智能出题：按科目 / 知识点 / 题型生成候选题。
     *
     * <p>返回内容分两部分：模型原文（{@code content}）与解析后的结构化草稿（{@code drafts}）。
     * 草稿**不会**自动入库——前端把草稿渲染成可勾选的表格，教师确认后才走
     * 与手工新建相同的 {@code /question} 接口写库。AI 依然不掌握写操作决策权。
     */
    AiGenQuestionVO genQuestion(AiGenQuestionDTO dto);

    /** 主观题辅助阅卷：给出建议分数与理由 */
    AiMarkSuggestionVO assistMark(AiAssistMarkDTO dto);

    /** 错题分析：知识点掌握度统计 + 教学建议（统计部分不依赖大模型） */
    AiAnalysisVO analysis(Long examId);
}
