package com.zhikao.mapper;
import org.apache.ibatis.annotations.*;
import java.util.*;
@Mapper
public interface CandidateEventMapper {
 @Select("SELECT revision,action,operator,created_at FROM candidate_event WHERE candidate_id=#{id} ORDER BY created_at,id")
 List<Map<String,Object>> events(Long id);
 @Insert("INSERT INTO candidate_event(id,candidate_id,revision,action,operator,payload) VALUES(#{id},#{candidateId},#{revision},#{action},#{operator},#{payload})")
 int append(@Param("id") String id,@Param("candidateId") Long candidateId,@Param("revision") Integer revision,@Param("action") String action,@Param("operator") String operator,@Param("payload") String payload);
}
