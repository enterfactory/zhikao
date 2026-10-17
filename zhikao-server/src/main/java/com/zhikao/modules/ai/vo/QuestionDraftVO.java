package com.zhikao.modules.ai.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一道「AI 生成但尚未入库」的题目草稿。
 *
 * <p>为什么要有这个类：一期智能出题只返回一段 Markdown 文本，
 * 教师必须自己复制、切到题库页、逐字段粘贴——演示时这一步非常尴尬。
 * 现在把模型返回的 JSON 解析成结构化草稿，前端直接渲染成可勾选的表格，
 * 勾完点「导入选中」即可入库。
 *
 * <p>但**边界没有变**：AI 依然不写库。草稿先回前端，由人勾选确认后
 * 才调用与手工新建完全相同的 {@code /question} 接口落库。
 * 换句话说，便捷性提升的是「输入方式」，不是「决策权」。
 */
@Data
public class QuestionDraftVO {

    /** 题干 */
    /** 目标科目 id（由请求带入；为空的草稿前端会要求先选科目） */
    private Long subjectId;

    private String content;

    /** 选项，key 为 A/B/C/D。判断题与填空/简答为空映射 */
    private Map<String, String> options = new LinkedHashMap<>();

    /** 参考答案 */
    private String answer;

    /** 解析 */
    private String analysis;

    /** 难度 1-5 */
    private Integer difficulty;

    /** 知识点 */
    private String knowledgePoint;

    /** 题型：沿用 {@code QuestionTypeEnum} 的取值 */
    private Integer type;

    /** 分值 */
    private BigDecimal score;

    /**
     * 解析/校验阶段的提示（如「缺少选项，单选题不可入库」）。
     * 前端把它显示在行内，让教师在勾选前就看到问题，而不是入库时才报错。
     */
    private List<String> warnings = new ArrayList<>();

    /** 是否可以直接入库（没有阻断性问题的草稿才允许勾选） */
    private boolean valid = true;

    /** 加上一条提示 */
    public void warn(String message) {
        warnings.add(message);
    }

    /** 标记为不可入库 */
    public void invalidate(String message) {
        warnings.add(message);
        this.valid = false;
    }
}
