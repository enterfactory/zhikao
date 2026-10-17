package com.zhikao.modules.learn.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 错题本条目：一道做错的题 + 我的作答 + 标准答案与解析。
 *
 * 关于 options 与 optionMap 并存：数据库里选项是 JSON 字符串，
 * MyBatis 直接映射成 Map 需要额外的 TypeHandler；沿用项目既有做法——
 * 实体/查询结果持字符串，服务层转 Map 后返回前端。原始串用 @JsonIgnore 挡在响应之外。
 */
@Data
public class WrongQuestionVO {

    private Long answerId;

    private Long examId;

    private String examName;

    private Long paperQuestionId;

    private Integer sort;

    private Integer type;

    private String typeName;

    private String content;

    /** 数据库中的原始选项 JSON，仅供服务层转换使用，不下发前端 */
    @JsonIgnore
    private String options;

    /** 转换后的选项，key 为 A/B/C/D */
    private Map<String, String> optionMap;

    /** 我的作答 */
    private String myAnswer;

    private String standardAnswer;

    private String analysis;

    private String knowledgePoint;

    private BigDecimal fullScore;

    private BigDecimal gotScore;

    private LocalDateTime submitTime;
}
