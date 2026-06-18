package com.lak.ai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 审计日志 — 按月分表，无 updateTime，禁止物理删除。
 * <p>
 * 表名由 Mybatis-Plus 动态表名拦截器在运行时替换为 audit_log_yyyyMM。
 */
@Getter
@Setter
@ToString
@TableName("audit_log")
public class AuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("trace_id")
    private String traceId;
    @TableField("session_id")
    private String sessionId;
    @TableField("user_id")
    private Long userId;
    @ToString.Exclude
    @TableField("request_body")
    private String requestBody;
    @ToString.Exclude
    @TableField("response_body")
    private String responseBody;
    @TableField("intent_type")
    private String intentType;
    private BigDecimal confidence;
    @ToString.Exclude
    @TableField("model_params")
    private String modelParams;
    @ToString.Exclude
    @TableField("model_response")
    private String modelResponse;
    @ToString.Exclude
    @TableField("retrieval_fragments")
    private String retrievalFragments;
    @TableField("latency_ms")
    private Integer latencyMs;
    private String status;
    @ToString.Exclude
    @TableField("error_message")
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
