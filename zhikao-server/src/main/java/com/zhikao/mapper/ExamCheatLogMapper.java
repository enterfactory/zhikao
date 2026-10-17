package com.zhikao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.entity.ExamCheatLog;
import com.zhikao.modules.exam.vo.CheatLogVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 作弊证据链 Mapper。
 */
@Mapper
public interface ExamCheatLogMapper extends BaseMapper<ExamCheatLog> {

    /**
     * 某场考试的全部作弊证据（按学生、次数排序）。
     * 带上题号，教师才能判断「是偶发一次」还是「一直切」。
     */
    @Select("""
            SELECT cl.id              AS id,
                   cl.exam_student_id AS exam_student_id,
                   su.real_name       AS student_name,
                   cl.cheat_type      AS cheat_type,
                   cl.seq             AS seq,
                   cl.happen_time     AS happen_time,
                   pq.sort            AS sort,
                   cl.remark          AS remark
            FROM exam_cheat_log cl
            JOIN exam_student es        ON es.id = cl.exam_student_id
            JOIN sys_user su            ON su.id = es.student_id
            LEFT JOIN paper_question pq ON pq.id = cl.paper_question_id
            WHERE cl.exam_id = #{examId}
            ORDER BY su.real_name, cl.seq
            """)
    List<CheatLogVO> selectByExam(@Param("examId") Long examId);
}
