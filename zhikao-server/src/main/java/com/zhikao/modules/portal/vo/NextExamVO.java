package com.zhikao.modules.portal.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 「下一场考试」提醒（本人班级已发布考试中最近一场未开始/进行中）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NextExamVO {

    private Long examId;

    private String examName;

    private String subjectName;

    /** 开考时间 */
    private LocalDateTime startAt;
}
