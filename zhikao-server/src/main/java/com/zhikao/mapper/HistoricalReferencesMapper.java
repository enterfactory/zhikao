package com.zhikao.mapper;
import org.apache.ibatis.annotations.*;
@Mapper public interface HistoricalReferencesMapper {
 @Select("""
 SELECT (SELECT COUNT(*) FROM exam_student WHERE student_id=#{id})
 + (SELECT COUNT(*) FROM exam_roster WHERE student_id=#{id})
 + (SELECT COUNT(*) FROM paper WHERE create_by=#{username})
 + (SELECT COUNT(*) FROM exam WHERE create_by=#{username})
 + (SELECT COUNT(*) FROM question WHERE create_by=#{username})
 + (SELECT COUNT(*) FROM sys_file WHERE create_by=#{username})
 + (SELECT COUNT(*) FROM practice_session WHERE user_id=#{id})
 """) long userReferences(@Param("id") Long id,@Param("username") String username);
 @Select("SELECT (SELECT COUNT(*) FROM exam WHERE class_id=#{id})+(SELECT COUNT(*) FROM exam_roster WHERE class_id=#{id})")
 long classReferences(@Param("id") Long id);
}
