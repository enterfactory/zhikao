package com.zhikao.modules.exam.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 考试列表 / 详情返回对象（教师视角）。
 */
@Data
public class ExamVO {

    private Integer releaseStatus;

    private Long id;

    private String examName;

    private Long paperId;

    private String paperName;

    private Long subjectId;

    private String subjectName;

    private Long classId;

    private String className;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 答题时长（分钟） */
    private Integer duration;

    /** 0未开始 1进行中 2已结束 */
    private Integer status;

    private String statusName;

    /** 0草稿 1已发布 */
    private Integer published;

    private Integer autoMark;

    private Integer cheatCheck;

    /** 试卷题数 */
    private Integer questionCount;

    /** 试卷总分 */
    private BigDecimal totalScore;

    /** 所属学期 id（可空） */
    private Long semesterId;

    /** 所属学期名称（列表回显用，学期不存在时为空） */
    private String semesterName;

    private String createBy;

    private LocalDateTime createTime;
}
