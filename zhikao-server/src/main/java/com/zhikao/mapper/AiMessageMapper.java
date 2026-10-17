package com.zhikao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhikao.entity.AiMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 会话消息 Mapper。
 */
@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessage> {
}
