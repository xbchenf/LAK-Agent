package com.lak.ai.service.audit;

import com.lak.ai.mapper.AuditLogMapper;
import com.lak.ai.model.entity.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 人工坐席操作审计 — WebSocket 消息不走 HTTP Filter Chain，需显式落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperatorAuditLogger {

    private final AuditLogMapper auditLogMapper;

    public void log(String sessionId, Long userId, String operation, String detail) {
        AuditLog audit = new AuditLog();
        audit.setTraceId(UUID.randomUUID().toString().replace("-", "").substring(0, 32));
        audit.setSessionId(sessionId);
        audit.setUserId(userId);
        audit.setIntentType(operation);  // 复用 intentType 字段存操作类型
        audit.setRequestBody(detail);
        audit.setStatus("SUCCESS");
        audit.setLatencyMs(0);
        auditLogMapper.insert(audit);
    }
}
