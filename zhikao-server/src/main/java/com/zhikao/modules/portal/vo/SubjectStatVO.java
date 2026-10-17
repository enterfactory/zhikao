package com.zhikao.modules.portal.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 科目题库进度行：该科目题库总量 + 本人已刷数。
 * 同时用作 Mapper 聚合结果的承载类型（先填 subjectId/totalQuestions 或 doneCount，再在服务层合并）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectStatVO {

    private Long subjectId;

    private String subjectName;

    /** 该科目题库总题数 */
    private Integer totalQuestions;

    /** 本人跨考试已作答过的该科目题目数（按源题去重） */
    private Integer doneCount;
}
