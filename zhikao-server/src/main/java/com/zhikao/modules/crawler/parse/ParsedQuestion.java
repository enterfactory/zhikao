package com.zhikao.modules.crawler.parse;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 解析中间产物：从原始内容里抽出的「结构化雏形」，尚未清洗与校验。
 *
 * <p>刻意与 crawl_question 实体解耦——解析器只管「把文本拆成字段」，
 * 字段合法性（题干够长、选项与题型匹配、去重）交给 Cleaner，
 * 归类打标（学段/科目/知识点/难度）交给 ClassifierTagger。三段各司其职、便于单测。
 */
@Data
public class ParsedQuestion {

    /** 题型码：1单选2多选3判断4填空5简答；解析器无法识别时为 null，交由后续兜底 */
    private Integer questionType;

    /** 题干 */
    private String content;

    /** 选项：key=A/B/C/D，value=选项内容 */
    private Map<String, String> options = new LinkedHashMap<>();

    /** 答案 */
    private String answer;

    /** 解析 */
    private String analysis;

    /** 学段（源里若自带，如「高中」；否则由 ClassifierTagger 推断） */
    private String grade;

    /** 年级（如「高一」「三年级」） */
    private String gradeLevel;

    /** 科目名（源里若自带；否则由 ClassifierTagger 关键词匹配 sys_subject.subject_name） */
    private String subjectName;

    /** 知识点提示（JSON 源里可显式给出，用于打标参考） */
    private String knowledgeHint;
}
