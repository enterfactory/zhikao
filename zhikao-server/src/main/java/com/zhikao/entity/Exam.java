package com.zhikao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 考试：把「试卷 × 班级」在指定时间窗内发布出去。
 * 状态机：0未开始 → 1进行中 → 2已结束（由 ExamService 按时间窗与发布情况推进）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("exam")
public class Exam {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer rosterFrozen;

    private String examName;

    private Long paperId;

    /** 参考班级 */
    private Long classId;

    /** 考试开始时间 */
    private LocalDateTime startTime;

    /** 考试结束时间 */
    private LocalDateTime endTime;

    /** 答题时长（分钟），从学生进入考试起算 */
    private Integer duration;

    /** 0未开始 1进行中 2已结束（按时间窗自动推进） */
    private Integer status;

    /** 0草稿 1已发布：未发布的考试学生不可见、不可进入 */
    private Integer published;

    /** 0未公布 1仅成绩 2成绩与解析；仅教师显式发布后开放。 */
    private Integer releaseStatus;

    /** 客观题自动阅卷 1开 0关 */
    private Integer autoMark;

    /** 防作弊（切屏计数）1开 0关 */
    private Integer cheatCheck;

    /** 所属学期（sys_semester.id），可空：学期模型是渐进接入的，不强制存量考试归类 */
    private Long semesterId;

    private String createBy;

    private LocalDateTime createTime;
}
