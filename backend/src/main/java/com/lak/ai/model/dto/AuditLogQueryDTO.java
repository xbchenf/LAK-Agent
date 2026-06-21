package com.lak.ai.model.dto;

import lombok.Data;

/**
 * 审计日志查询参数。
 */
@Data
public class AuditLogQueryDTO {
    /** 用户ID筛选 */
    private Long userId;
    /** 状态筛选: SUCCESS / FAIL / FALLBACK */
    private String status;
    /** 关键词模糊搜索（traceId / requestUri） */
    private String keyword;
    /** 开始日期（yyyy-MM-dd） */
    private String startDate;
    /** 结束日期（yyyy-MM-dd） */
    private String endDate;
    /** 目标月份（yyyyMM），默认当前月 */
    private String month;
    /** 页码，默认1 */
    private Integer page = 1;
    /** 每页条数，默认20 */
    private Integer size = 20;
}
