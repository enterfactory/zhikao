package com.zhikao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.entity.QuestionKnowledge;
import org.apache.ibatis.annotations.Mapper;

/**
 * 题目-知识点关联 Mapper。
 */
@Mapper
public interface QuestionKnowledgeMapper extends BaseMapper<QuestionKnowledge> {
}
