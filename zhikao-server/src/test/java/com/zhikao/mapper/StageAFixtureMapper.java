package com.zhikao.mapper;
import org.apache.ibatis.annotations.*;
@Mapper
public interface StageAFixtureMapper {
 @Select("SELECT MIN(id) FROM subject")
 Long firstSubjectId();
 @Select("SELECT class_id FROM sys_user WHERE username='student'")
 Long studentClassId();
 @Select("SELECT MAX(id) FROM question WHERE content=#{p0}")
 Long questionIdByContent(@Param("p0") Object p0);
 @Select("SELECT ea.id FROM exam_answer ea JOIN paper_question pq ON pq.id=ea.paper_question_id JOIN exam_student es ON es.id=ea.exam_student_id WHERE es.exam_id=#{p0} AND pq.type=5")
 Long essayAnswerId(@Param("p0") Object p0);
 @Select("SELECT COUNT(*) FROM exam_student WHERE exam_id=#{p0}")
 Integer attemptCount(@Param("p0") Object p0);
 @Select("SELECT es.status,es.total_score,ea.answer FROM exam_student es JOIN exam_answer ea ON ea.exam_student_id=es.id WHERE es.exam_id=#{p0}")
 java.util.Map<String,Object> answerState(@Param("p0") Object p0);
 @Update("UPDATE exam SET end_time=#{p0} WHERE id=#{p1}")
 int setExamEnd(@Param("p0") Object p0,@Param("p1") Object p1);
 @Select("SELECT ea.id FROM exam_answer ea JOIN exam_student es ON es.id=ea.exam_student_id WHERE es.exam_id=#{p0}")
 Long singleAnswerId(@Param("p0") Object p0);
 @Select("SELECT status,objective_score,subjective_score FROM exam_student WHERE exam_id=#{p0}")
 java.util.Map<String,Object> attemptScores(@Param("p0") Object p0);
 @Insert("INSERT INTO crawl_question(raw_id,stage,question_type,content,answer,revision,formula_confirmed) VALUES(0,'TAGGED',1,'复核测试的题目内容','B',1,0)")
 int insertCandidate();
 @Select("SELECT MAX(id) FROM crawl_question")
 Long lastCandidateId();
 @Select("SELECT COUNT(*) FROM candidate_event WHERE candidate_id=#{p0}")
 Integer candidateEventCount(@Param("p0") Object p0);
 @Insert("INSERT INTO crawl_task(source_id,status) VALUES(0,'PENDING')")
 int insertPendingCrawl();
 @Select("SELECT MAX(id) FROM crawl_task")
 Long lastCrawlId();
 @Update("UPDATE crawl_task SET lease_until=CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id=#{id}")
 int expireCrawlLease(@Param("id") Long id);
 @Update("UPDATE crawl_task SET status='FAILED' WHERE id=#{p0}")
 int failCrawl(@Param("p0") Object p0);
 @Select("SELECT status FROM exam_student WHERE exam_id=#{p0}")
 Integer attemptStatus(@Param("p0") Object p0);
 @Select("SELECT COUNT(*) FROM exam_answer ea JOIN exam_student es ON es.id=ea.exam_student_id WHERE es.exam_id=#{p0}")
 Integer answerCount(@Param("p0") Object p0);
 @Select("SELECT COUNT(*) FROM sys_user")
 Integer userCount();
 @Select("SELECT username FROM sys_user")
 String onlyUsername();
 @Select("SELECT COUNT(*) FROM question")
 Integer questionCount();
 @Select("SELECT COUNT(*) FROM sys_class")
 Integer classCount();
 @Select("SELECT COUNT(*) FROM sys_permission")
 Integer permissionCount();
 @Select("SELECT COUNT(*) FROM sys_dict_item")
 Integer dictItemCount();
 @Update("DELETE FROM sys_role_permission WHERE role_id=(SELECT id FROM sys_role WHERE role_code='teacher')")
 int removeTeacherGrants();
 @Select("SELECT password FROM sys_user")
 String onlyPasswordHash();
 @Select("SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_role r ON r.id=rp.role_id WHERE r.role_code='teacher'")
 Integer teacherGrantCount();
 @Update("ALTER TABLE exam_answer ADD CONSTRAINT test_collection_failure CHECK (paper_question_id <> ${id})")
 void addFailure(@Param("id") Long id);
 @Update("ALTER TABLE exam_answer DROP CONSTRAINT test_collection_failure") void dropFailure();
 @Update("UPDATE sys_export_task SET lease_until=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE id=#{id}")
 void expireExport(Long id);
 @Select("SELECT version()") String databaseVersion();
 @Select("SELECT current_schema()") String schema();
 @Update("DROP SCHEMA IF EXISTS ${schema} CASCADE") void dropTestSchemaSql(@Param("schema") String schema);
 default void dropTestSchema(String schema) {
  if(!schema.matches("zhikao_test_[a-z]+_[0-9a-f]{12}")) throw new IllegalArgumentException("Not a test schema");
  dropTestSchemaSql(schema);
 }
}
