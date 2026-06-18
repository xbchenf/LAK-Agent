package com.lak.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lak.ai.model.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
