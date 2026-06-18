package com.lak.ai.security.interceptor;

import com.lak.ai.mapper.AuditLogMapper;
import com.lak.ai.model.entity.AuditLog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 审计日志拦截器 — 捕获请求/响应，写入 audit_log 表。
 * <p>
 * Filter Chain order=3（在 TraceId 之后、Auth 之前）。
 */
@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class AuditLogInterceptor implements HandlerInterceptor {

    private final AuditLogMapper auditLogMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        String traceId = MDC.get("traceId");
        log.debug("审计日志 [请求] traceId={}, method={}, uri={}, remoteAddr={}",
                traceId, request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
        request.setAttribute("audit.startTime", System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute("audit.startTime");
        long costMs = startTime != null ? System.currentTimeMillis() - startTime : -1;
        String traceId = MDC.get("traceId");

        AuditLog auditLog = new AuditLog();
        auditLog.setTraceId(traceId);
        auditLog.setLatencyMs((int) costMs);
        auditLog.setStatus(ex == null ? "SUCCESS" : "FAIL");
        if (ex != null) {
            auditLog.setErrorMessage(ex.getMessage());
        }
        auditLogMapper.insert(auditLog);

        log.debug("审计日志 [响应] traceId={}, status={}, costMs={}",
                traceId, response.getStatus(), costMs);
    }
}
