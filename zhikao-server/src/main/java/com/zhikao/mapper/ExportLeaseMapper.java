package com.zhikao.mapper;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface ExportLeaseMapper {
 @Update("UPDATE sys_export_task SET lease_owner=#{token},lease_until=CURRENT_TIMESTAMP+INTERVAL '2 minutes',attempts=attempts+1 WHERE id=#{id} AND status=0 AND attempts<3 AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP)")
 int claim(@Param("id") Long id,@Param("token") String token);
 @Update("UPDATE sys_export_task SET lease_until=CURRENT_TIMESTAMP+INTERVAL '2 minutes' WHERE id=#{id} AND status=0 AND lease_owner=#{token} AND lease_until>=CURRENT_TIMESTAMP")
 int heartbeat(@Param("id") Long id,@Param("token") String token);
 @Update("UPDATE sys_export_task SET status=#{status},file_id=#{fileId},error_msg=#{error},finish_time=CURRENT_TIMESTAMP,lease_until=NULL WHERE id=#{id} AND status=0 AND lease_owner=#{token} AND lease_until>=CURRENT_TIMESTAMP")
 int complete(@Param("id") Long id,@Param("token") String token,@Param("status") int status,@Param("fileId") Long fileId,@Param("error") String error);
 @Update("UPDATE sys_export_task SET status=2,error_msg='任务中断重试达到上限，请重新导出',finish_time=CURRENT_TIMESTAMP WHERE status=0 AND attempts>=3 AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP)")
 int failExhausted();
 @Select("SELECT id FROM sys_export_task WHERE status=0 AND attempts<3 AND (lease_until IS NULL OR lease_until<CURRENT_TIMESTAMP) ORDER BY id LIMIT 10")
 List<Long> recoverable();
}
