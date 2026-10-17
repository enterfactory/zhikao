package com.zhikao.modules.exam.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 题目返回对象：把库中的选项 JSON 字符串还原成 Map，前端可直接渲染。
 */
@Data
public class QuestionVO {

    private Long id;

    private Long subjectId;

    /** 科目名称，列表直接展示，避免前端二次查询 */
    private String subjectName;

    /** 1单选 2多选 3判断 4填空 5简答 */
    private Integer type;

    /** 题型中文名 */
    private String typeName;

    /** 是否客观题（前端据此决定是否需要教师批改） */
    private Boolean objective;

    private String content;

    /** 选项：A/B/C/D -> 内容（有序） */
    private Map<String, String> options = new LinkedHashMap<>();

    private String answer;

    private String analysis;

    private Integer difficulty;

    private String knowledgePoint;

    private BigDecimal score;

    private String createBy;

    private LocalDateTime createTime;
}
