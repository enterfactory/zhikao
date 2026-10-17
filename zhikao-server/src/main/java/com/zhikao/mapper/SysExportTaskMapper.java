package com.zhikao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.entity.SysExportTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 异步导出任务 Mapper。
 */
@Mapper
public interface SysExportTaskMapper extends BaseMapper<SysExportTask> {
}
