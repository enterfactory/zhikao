package com.zhikao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.entity.CrawlSource;
import org.apache.ibatis.annotations.Mapper;

/**
 * 爬虫来源 Mapper：标准 CRUD 由 BaseMapper 提供，本模块无自定义 SQL。
 */
@Mapper
public interface CrawlSourceMapper extends BaseMapper<CrawlSource> {
}
