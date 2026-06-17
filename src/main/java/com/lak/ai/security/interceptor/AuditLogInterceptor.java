package com.lak.ai.security.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 审计日志拦截器 — 捕获请求/响应，记录入站日志。
 * <p>
 * Filter Chain order=3（在 TraceId 之后、Auth 之前）。
 * 使用 Spring Interceptor 而非 Filter，以便访问 Handler 信息。
 */
@Slf4j
@Component
@Order(3)
public class AuditLogInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        String traceId = MDC.get("traceId");
        log.info("审计日志 [请求] traceId={}, method={}, uri={}, remoteAddr={}",
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
        log.info("审计日志 [响应] traceId={}, status={}, costMs={}",
                traceId, response.getStatus(), costMs);
        // 后续 P0 实现: 将完整请求/响应内容写入 audit_log 表
    }
}
