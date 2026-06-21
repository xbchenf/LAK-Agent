package com.lak.ai.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 审计日志视图对象 — 列表和详情共用。
 * <p>
 * 含解析后的 username 和操作类型中文名。
 */
@Data
public class AuditLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String traceId;
    private String sessionId;
    private Long userId;
    private String username;          // 解析自 sys_user
    private String requestUri;        // 请求 URI
    private String operation;         // 操作类型中文名（URI→中文映射）
    private String requestBody;
    private String responseBody;
    private String intentType;
    private BigDecimal confidence;
    private String modelParams;
    private String modelResponse;
    private String retrievalFragments;
    private Integer latencyMs;
    private String status;
    private String errorMessage;
    private LocalDateTime createTime;
}
