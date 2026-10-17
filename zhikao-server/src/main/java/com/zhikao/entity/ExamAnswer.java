package com.zhikao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 答题明细：某学生某道试卷题目的作答与得分。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("exam_answer")
public class ExamAnswer {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 exam_student.id */
    private Long examStudentId;

    /** 关联 paper_question.id（快照题目） */
    private Long paperQuestionId;

    /** 学生作答 */
    private String answer;

    /** 是否客观题 1是 0否 */
    private Integer isObjective;

    /** 本题得分；主观题未批改时为 null */
    private BigDecimal score;

    /** 1对 0错 null未判 */
    private Integer correct;

    /** 0未批改 1已批改 */
    private Integer marked;

    /**
     * 批改乐观锁版本号。
     *
     * <p>存在的原因：两位教师同时给同一份答卷打分时，原来的写法是
     * {@code selectById → 改字段 → updateById}，后写的会静默覆盖先写的。
     * 打分动作允许改分，但不允许「无感知地互相覆盖」，所以每次打分用
     * {@code WHERE id = ? AND mark_version = ?} 做条件更新，冲突时返回明确提示。
     */
    private Integer markVersion;

    private Integer answerVersion;
}
