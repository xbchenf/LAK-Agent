package com.lak.ai.service.audit;

import com.lak.ai.mapper.AuditLogMapper;
import com.lak.ai.model.entity.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * 审计日志 AOP 切面 — 在 @AuditLog 注解的方法上自动记录入参/出参/耗时。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogMapper auditLogMapper;

    /**
     * 拦截 ChatController.sendMessage() 和 TicketController.createTicket()。
     */
    @Around("@annotation(com.lak.ai.service.audit.AuditLog)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        String traceId = MDC.get("traceId");
        String methodName = joinPoint.getSignature().toShortString();

        AuditLog auditLog = new AuditLog();
        auditLog.setTraceId(traceId);
        auditLog.setStatus("SUCCESS");

        try {
            Object result = joinPoint.proceed();
            long costMs = System.currentTimeMillis() - start;
            auditLog.setLatencyMs((int) costMs);
            auditLogMapper.insert(auditLog);
            log.debug("审计日志记录成功, method={}, costMs={}", methodName, costMs);
            return result;
        } catch (Exception e) {
            auditLog.setStatus("FAIL");
            auditLog.setErrorMessage(e.getMessage());
            auditLog.setLatencyMs((int) (System.currentTimeMillis() - start));
            auditLogMapper.insert(auditLog);
            throw e;
        }
    }
}
