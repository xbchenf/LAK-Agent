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

    /** 跳过审计记录的路径（健康检查等） */
    private static final String[] SKIP_PREFIXES = {"/health", "/actuator", "/api/v1/health", "/error"};

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        String uri = request.getRequestURI();
        // 跳过非业务路径
        for (String prefix : SKIP_PREFIXES) {
            if (uri.startsWith(prefix)) return true;
        }
        String traceId = MDC.get("traceId");
        log.debug("审计日志 [请求] traceId={}, method={}, uri={}, remoteAddr={}",
                traceId, request.getMethod(), uri, request.getRemoteAddr());
        request.setAttribute("audit.startTime", System.currentTimeMillis());
        // 暂存 URI 供 afterCompletion 使用
        request.setAttribute("audit.requestBody",
                "{\"method\":\"" + request.getMethod() + "\",\"uri\":\"" + uri + "\"}");
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute("audit.startTime");
        if (startTime == null) return; // 被跳过的请求
        long costMs = System.currentTimeMillis() - startTime;
        String traceId = MDC.get("traceId");

        AuditLog auditLog = new AuditLog();
        auditLog.setTraceId(traceId);
        auditLog.setLatencyMs((int) costMs);
        auditLog.setStatus(ex == null ? "SUCCESS" : "FAIL");
        if (ex != null) {
            auditLog.setErrorMessage(ex.getMessage());
        }
        // AuthFilter 在 afterCompletion 之前已执行，此时可取 userId
        Long userId = (Long) request.getAttribute("userId");
        if (userId != null) {
            auditLog.setUserId(userId);
        }
        // 写入 request_body（method + uri JSON）
        String reqBody = (String) request.getAttribute("audit.requestBody");
        if (reqBody != null) {
            auditLog.setRequestBody(reqBody);
        }

        auditLogMapper.insert(auditLog);

        log.debug("审计日志 [响应] traceId={}, status={}, costMs={}",
                traceId, response.getStatus(), costMs);
    }
}
