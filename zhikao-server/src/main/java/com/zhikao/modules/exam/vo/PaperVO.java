package com.zhikao.modules.exam.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 试卷列表返回对象。
 */
@Data
public class PaperVO {

    private Long id;

    private String paperName;

    private Long subjectId;

    /** 科目名称，列表直接展示 */
    private String subjectName;

    private BigDecimal totalScore;

    private Integer totalDuration;

    /** 1手动 2随机 */
    private Integer genType;

    /** 组卷方式中文名 */
    private String genTypeName;

    private Integer questionCount;

    /** 0草稿 1已发布 */
    private Integer status;

    private String createBy;

    private LocalDateTime createTime;
}
