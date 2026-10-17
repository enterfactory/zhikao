package com.zhikao.mapper;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface ExamRosterMapper {
 @Insert("""
  INSERT INTO exam_roster(exam_id,student_id,class_id,student_name,class_name)
  SELECT e.id,u.id,u.class_id,u.real_name,c.class_name FROM exam e
  JOIN sys_user u ON u.class_id=e.class_id AND u.user_type=3 AND u.status=1
  LEFT JOIN sys_class c ON c.id=u.class_id WHERE e.id=#{id} ON CONFLICT DO NOTHING
  """) int freeze(@Param("id") Long id);
 @Select("SELECT COUNT(*) FROM exam_roster WHERE exam_id=#{id}") long count(@Param("id") Long id);
 @Select("SELECT COUNT(*) FROM exam_roster WHERE exam_id=#{examId} AND student_id=#{studentId}")
 long contains(@Param("examId") Long examId,@Param("studentId") Long studentId);
 @Select("SELECT student_id FROM exam_roster WHERE exam_id=#{id} ORDER BY student_id") List<Long> students(@Param("id") Long id);
 @Select("SELECT exam_id FROM exam_roster WHERE student_id=#{id} UNION SELECT exam_id FROM exam_student WHERE student_id=#{id}") List<Long> exams(@Param("id") Long id);
}
