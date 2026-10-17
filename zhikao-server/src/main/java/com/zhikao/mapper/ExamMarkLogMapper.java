package com.zhikao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.entity.ExamMarkLog;
import com.zhikao.modules.exam.vo.MarkLogVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 批改留痕 Mapper。
 */
@Mapper
public interface ExamMarkLogMapper extends BaseMapper<ExamMarkLog> {

    /**
     * 某场考试的全部批改留痕（按时间倒序，最近改的排最前）。
     *
     * <p>一次 JOIN 把学生姓名、题号、题干都带出来：教师看留痕时关心的是
     * 「张三的第 2 题被改过」，而不是一个孤零零的 answer_id。
     */
    @Select("""
            SELECT ml.id               AS id,
                   ml.exam_student_id  AS exam_student_id,
                   ml.exam_answer_id   AS exam_answer_id,
                   ml.action           AS action,
                   ml.old_score        AS old_score,
                   ml.new_score        AS new_score,
                   ml.reason           AS reason,
                   ml.operator         AS operator,
                   ml.create_time      AS create_time,
                   su.real_name        AS student_name,
                   pq.sort             AS sort,
                   pq.content          AS question_content
            FROM exam_mark_log ml
            JOIN exam_student es   ON es.id = ml.exam_student_id
            JOIN sys_user su       ON su.id = es.student_id
            JOIN exam_answer ea    ON ea.id = ml.exam_answer_id
            JOIN paper_question pq ON pq.id = ea.paper_question_id
            WHERE ml.exam_id = #{examId}
            ORDER BY ml.id DESC
            """)
    List<MarkLogVO> selectByExam(@Param("examId") Long examId);
}
