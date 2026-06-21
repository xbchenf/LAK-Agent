package com.lak.ai.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lak.ai.model.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {

    /**
     * 分页条件查询审计日志。
     * <p>
     * 表名由 MybatisPlusConfig 动态替换为 audit_log_yyyyMM。
     */
    default Page<AuditLog> selectPageWithFilters(
            Page<AuditLog> page,
            Long userId, String status, String keyword,
            LocalDateTime startTime, LocalDateTime endTime) {

        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<AuditLog>()
                .eq(userId != null, AuditLog::getUserId, userId)
                .eq(status != null && !status.isBlank(), AuditLog::getStatus, status)
                .ge(startTime != null, AuditLog::getCreateTime, startTime)
                .le(endTime != null, AuditLog::getCreateTime, endTime)
                .orderByDesc(AuditLog::getCreateTime);

        // 关键词：traceId 模糊匹配 或 requestBody 中包含关键词
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w
                    .like(AuditLog::getTraceId, keyword)
                    .or()
                    .like(AuditLog::getRequestBody, keyword));
        }

        return selectPage(page, wrapper);
    }
}
