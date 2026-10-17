package com.zhikao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 批改留痕。
 *
 * <p>设计前提：<b>改分必须被允许，但必须留痕。</b>
 * 批改是个会出错的动作——教师看漏一问、分数填错、复核后调整，这些都会发生。
 * 正确做法不是禁止改分（那会让教师不敢下手），而是把每次改动记下来，
 * 让「这道题从 5 分变成 8 分，是张老师在 9 月 21 日因为『步骤完整』改的」可以被回答。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("exam_mark_log")
public class ExamMarkLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属答卷 exam_student.id */
    private Long examStudentId;

    /** 被批改的答题明细 exam_answer.id */
    private Long examAnswerId;

    /** 冗余的考试 id：查「这场考试改过哪些分」时不用再 JOIN 一次 */
    private Long examId;

    /** 1首评 2改分 */
    private Integer action;

    /** 改前分数；首评时为 null */
    private BigDecimal oldScore;

    private BigDecimal newScore;

    /** 改分理由：改分必填，首评可空 */
    private String reason;

    /** 操作人账号 */
    private String operator;

    private LocalDateTime createTime;
}
