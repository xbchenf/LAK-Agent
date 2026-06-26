package com.lak.ai.model.dto;

import lombok.Data;

/**
 * 文档列表查询参数。
 */
@Data
public class DocumentQueryDTO {
    /** 文档类型筛选: POLICY / PROCEDURE */
    private String docType;
    /** 状态筛选: DRAFT / ACTIVE / EXPIRED */
    private String status;
    /** 标题关键词模糊搜索 */
    private String keyword;
    /** 页码，默认1 */
    private Integer page = 1;
    /** 每页条数，默认10 */
    private Integer size = 10;
}
