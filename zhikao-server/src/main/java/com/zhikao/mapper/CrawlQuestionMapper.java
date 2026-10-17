package com.zhikao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.entity.CrawlQuestion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 清洗待审题 Mapper：标准 CRUD 由 BaseMapper 提供。
 * 阶段计数（stats）用 selectCount + 阶段条件即可，无需自定义 SQL。
 */
@Mapper
public interface CrawlQuestionMapper extends BaseMapper<CrawlQuestion> {
}
