package com.zhikao.mapper;
import org.apache.ibatis.annotations.*;
import java.util.*;
@Mapper
public interface PracticeMapper {
 List<Map<String,Object>> topics(@Param("userId") Long userId);
 List<Map<String,Object>> available(@Param("userId") Long userId,@Param("topic") String topic,@Param("wrongOnly") boolean wrongOnly);
 int create(@Param("id") String id,@Param("userId") Long userId,@Param("topic") String topic);
 int addItem(@Param("id") String id,@Param("sessionId") String sessionId,@Param("q") Map<String,Object> q);
 List<Map<String,Object>> items(@Param("id") String id);
 List<Map<String,Object>> item(@Param("id") String id,@Param("sessionId") String sessionId);
 int answer(@Param("id") String id,@Param("answer") String answer,@Param("correct") Integer correct);
 List<Map<String,Object>> history(@Param("userId") Long userId);
 String require(@Param("id") String id,@Param("userId") Long userId,@Param("lock") boolean lock);
}
