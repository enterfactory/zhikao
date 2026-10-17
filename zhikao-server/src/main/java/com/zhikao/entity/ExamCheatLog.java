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
 * 作弊证据链：一次异常行为的完整记录。
 *
 * <p>只存一个 {@code cheat_count} 计数时，能回答的只有「这个学生切过屏吗」，
 * 回答不了「他是第 3 分钟切的还是交卷前切的」「是在第 2 题切的还是一直切」。
 * 教师要做处置决定，缺的正是后面这一类信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("exam_cheat_log")
public class ExamCheatLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** exam_student.id */
    private Long examStudentId;

    private Long examId;

    /** 异常类型：blur（失焦/切屏）/ copy / paste / fullscreen_exit */
    private String cheatType;

    /** 发生时刻 */
    private LocalDateTime happenTime;

    /** 发生时的题目快照 id，可为空（学生可能还没点开题） */
    private Long paperQuestionId;

    private String remark;

    /** 本次是该答卷的第几次异常，与 exam_student.cheat_count 对齐 */
    private Integer seq;
}
