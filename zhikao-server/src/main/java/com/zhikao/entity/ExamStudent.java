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
 * 考试-学生参与记录：一个学生在一场考试中的状态与得分。
 * 状态机：0待考 → 1考试中 → 2已交卷 → 3已阅卷。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("exam_student")
public class ExamStudent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long examId;

    /** 学生 user_id */
    private Long studentId;

    /** 0待考 1考试中 2已交卷 3已阅卷 */
    private Integer status;

    /** 进入考试时间（答题时长从此刻起算） */
    private LocalDateTime enterTime;

    private LocalDateTime submitTime;

    /** 客观题得分（交卷时自动判定） */
    private BigDecimal objectiveScore;

    /** 主观题得分（教师批改后累计） */
    private BigDecimal subjectiveScore;

    /** 总分 */
    private BigDecimal totalScore;

    /** 切屏等异常次数（防作弊开启时累计） */
    private Integer cheatCount;

    /**
     * 交卷方式：1手动 2超时自动收卷 3作弊强制收卷。
     *
     * <p>没有这个字段时，「学生自己交的」和「系统帮他收的」在成绩表里完全一样，
     * 教师看到一份零分卷无法判断是学生弃考还是系统超时收卷。
     */
    private Integer submitType;
}
