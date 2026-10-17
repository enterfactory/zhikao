package com.zhikao.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.entity.NotificationOutbox;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface NotificationOutboxMapper extends BaseMapper<NotificationOutbox> {
 @Select("SELECT id FROM notification_outbox WHERE delivered_at IS NULL AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY id LIMIT 50")
 List<Long> pending();
 @Select("SELECT * FROM notification_outbox WHERE id=#{id} AND delivered_at IS NULL AND next_attempt_at<=CURRENT_TIMESTAMP FOR UPDATE SKIP LOCKED")
 NotificationOutbox lockPending(@Param("id") Long id);
 @Insert("""
  INSERT INTO notification_outbox(event_key,user_ids,type,title,content,biz_type,biz_id)
  VALUES(#{eventKey},#{userIds},#{type},#{title},#{content},#{bizType},#{bizId})
  ON CONFLICT(event_key) DO NOTHING
  """)
 int enqueue(NotificationOutbox row);
 @Insert("""
  INSERT INTO sys_notification(user_id,type,title,content,biz_type,biz_id,read_flag,create_time,event_key)
  VALUES(#{recipient},#{row.type},#{row.title},#{row.content},#{row.bizType},#{row.bizId},0,CURRENT_TIMESTAMP,#{row.eventKey})
  ON CONFLICT(event_key,user_id) DO NOTHING
  """)
 int deliver(@Param("row") NotificationOutbox row,@Param("recipient") Long recipient);
 @Update("""
  UPDATE notification_outbox SET attempts=attempts+1,last_error='DELIVERY_FAILED',
   next_attempt_at=CURRENT_TIMESTAMP+INTERVAL '10 seconds' WHERE id=#{id} AND delivered_at IS NULL
  """)
 int failed(@Param("id") Long id);
 @Update("UPDATE notification_outbox SET delivered_at=CURRENT_TIMESTAMP,last_error=NULL WHERE id=#{id}")
 int delivered(@Param("id") Long id);
}
