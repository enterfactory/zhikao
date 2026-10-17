package com.zhikao.modules.crawler.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 待审/各阶段题目回显对象。
 * 选项以 Map 形式回显（与主库题目一致的 A/B/C/D 结构），便于审核页直接渲染。
 */
@Data
public class CrawlQuestionVO {

    private Long id;

    private Integer sourcePage;
    private String sourceAnchor,sourceBbox,locatorMethod;
    private Long reviewMilliseconds;

    private Integer revision;
    private Integer formulaConfirmed;
    private Long rawId;
    private String stage;
    private String grade;
    private String gradeLevel;
    private Long subjectId;
    private String subjectName;
    private Integer questionType;
    /** 题型中文名（单选/多选/判断/填空/简答） */
    private String typeName;
    private String content;
    private Map<String, String> options;
    private String answer;
    private String analysis;
    private Integer difficulty;
    private String knowledgeTags;
    private String qualityFlags;
    private String reviewNote;
    private Long promotedQuestionId;
    private LocalDateTime cleanTime;
    private LocalDateTime createTime;
}
