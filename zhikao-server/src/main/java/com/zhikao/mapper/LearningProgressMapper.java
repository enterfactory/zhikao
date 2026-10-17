package com.zhikao.mapper;
import org.apache.ibatis.annotations.*;
import com.zhikao.modules.portal.vo.SubjectStatVO;
import java.util.List;
@Mapper
public interface LearningProgressMapper {
 @Select("""
  WITH accessible AS (
   SELECT DISTINCT pq.question_id,p.subject_id FROM paper_question pq JOIN paper p ON p.id=pq.paper_id
   JOIN exam e ON e.paper_id=p.id JOIN exam_student es ON es.exam_id=e.id
   WHERE es.student_id=#{userId} AND es.status=3 AND e.release_status=2 AND e.end_time<=CURRENT_TIMESTAMP
  ), attempted AS (
   SELECT DISTINCT pq.question_id FROM exam_answer ea JOIN exam_student es ON es.id=ea.exam_student_id
   JOIN paper_question pq ON pq.id=ea.paper_question_id
   WHERE es.student_id=#{userId} AND LENGTH(TRIM(COALESCE(ea.answer,'')))>0
   UNION SELECT DISTINCT pq.question_id FROM practice_item i JOIN practice_session ps ON ps.id=i.session_id
   JOIN paper_question pq ON pq.id=i.snapshot_id WHERE ps.user_id=#{userId} AND i.answered_at IS NOT NULL
  )
  SELECT a.subject_id,s.subject_name,COUNT(*) AS total_questions,
   SUM(CASE WHEN t.question_id IS NOT NULL THEN 1 ELSE 0 END) AS done_count
  FROM accessible a LEFT JOIN attempted t ON t.question_id=a.question_id LEFT JOIN subject s ON s.id=a.subject_id
  GROUP BY a.subject_id,s.subject_name ORDER BY a.subject_id
  """) List<SubjectStatVO> progress(@Param("userId") Long userId);
}
