package com.zhikao.modules.ai.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能出题结果：既给「人看的正文」，也给「机器能用的草稿」。
 *
 * <p>两者都返回是有意的：
 * <ul>
 *   <li>{@link #content} 是模型原始输出。JSON 解析失败时它是唯一的兜底，
 *       教师至少还能看到内容并手工处理；同时它也是排查提示词问题的依据。</li>
 *   <li>{@link #drafts} 是解析后的结构化草稿，前端据此渲染可勾选的导入表格。</li>
 * </ul>
 * 只返回其中任何一个都会丢信息：只给正文等于没解决便捷性，
 * 只给草稿则在模型偶尔输出不规范时让用户什么都看不到。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiGenQuestionVO extends AiTextVO {

    /** 解析出的题目草稿 */
    private List<QuestionDraftVO> drafts = new ArrayList<>();

    /** 解析说明：成功解析了几道、跳过了几道、为什么跳过 */
    private String parseNote;
}
