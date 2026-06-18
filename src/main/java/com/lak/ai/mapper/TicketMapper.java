package com.lak.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lak.ai.model.entity.Ticket;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {
}
