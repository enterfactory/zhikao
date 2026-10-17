package com.zhikao.mapper;
import org.apache.ibatis.annotations.*;
@Mapper
public interface PaperKnowledgeMapper {
 @Insert("""
  INSERT INTO paper_question_knowledge(paper_question_id,kp_id,name,subject_id)
  SELECT #{paperQuestionId},k.id,k.name,k.subject_id FROM question_knowledge qk
  JOIN knowledge_point k ON k.id=qk.kp_id WHERE qk.question_id=#{questionId}
  ON CONFLICT DO NOTHING
  """) int snapshot(@Param("paperQuestionId") Long paperQuestionId,@Param("questionId") Long questionId);
}
