package com.zhikao.modules.exam.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 学生视角的考试条目：考试信息 + 本人在该场考试的状态与得分。
 */
@Data
public class StudentExamVO {

    /** 考试 id */
    private Long id;

    private Integer releaseStatus;

    private String examName;

    private String paperName;

    private String subjectName;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer duration;

    /** 考试状态 0未开始 1进行中 2已结束 */
    private Integer status;

    private String statusName;

    private Integer questionCount;

    /** 试卷总分 */
    private BigDecimal totalScore;

    /** 本人参与状态：null 表示还没进入过 */
    private Integer myStatus;

    private String myStatusName;

    /** 本人客观题得分 */
    private BigDecimal objectiveScore;

    /** 本人主观题得分 */
    private BigDecimal subjectiveScore;

    /** 本人总分 */
    private BigDecimal myTotalScore;

    /** 剩余答题秒数（考试中才有值），前端据此倒计时，服务端为准 */
    private Long remainingSeconds;

    /** 最新一条考试公告（无公告为 null），让学生在列表页就能看到通知 */
    private String announcement;
}
