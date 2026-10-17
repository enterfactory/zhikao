package com.zhikao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.entity.CrawlTask;
import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 爬虫任务 Mapper：标准 CRUD 由 BaseMapper 提供。
 */
@Mapper
public interface CrawlTaskMapper extends BaseMapper<CrawlTask> {
 int claim(@Param("id") Long id,@Param("token") String token,@Param("until") LocalDateTime until);
 int fence(@Param("id") Long id,@Param("token") String token,@Param("until") LocalDateTime until);
 int failExhausted();
 List<Long> recoverable();
}
