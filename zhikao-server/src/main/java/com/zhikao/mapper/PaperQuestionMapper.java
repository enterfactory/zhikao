package com.zhikao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.entity.PaperQuestion;
import org.apache.ibatis.annotations.Mapper;

/**
 * 试卷题目（快照）Mapper。
 */
@Mapper
public interface PaperQuestionMapper extends BaseMapper<PaperQuestion> {
}
