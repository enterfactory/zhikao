package com.zhikao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.entity.AiTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 任务记录 Mapper。
 */
@Mapper
public interface AiTaskMapper extends BaseMapper<AiTask> {
}
