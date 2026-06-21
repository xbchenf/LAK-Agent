package com.lak.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lak.ai.model.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {
}
