package com.lak.ai.common.exception;

import com.lak.ai.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理 — 统一将异常转换为 ApiResponse。
 * <p>
 * 遵循阿里巴巴Java开发手册 — 不在 Controller 中写 try-catch，由此类统一拦截。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ===== 业务异常 =====

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthException(AuthException e) {
        log.warn("认证异常, code={}, message={}", e.getCode(), e.getMessage());
        HttpStatus status = mapAuthHttpStatus(e.getCode());
        return ResponseEntity.status(status)
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    private HttpStatus mapAuthHttpStatus(int code) {
        // LAK-01-003: 账户已禁用 → 403 Forbidden
        if (code == 1_003) return HttpStatus.FORBIDDEN;
        // LAK-01-004/005: 验证码错误/过期 → 400 Bad Request
        if (code == 1_004 || code == 1_005) return HttpStatus.BAD_REQUEST;
        // 其余认证异常 → 401 Unauthorized
        return HttpStatus.UNAUTHORIZED;
    }

    @ExceptionHandler(SensitiveWordException.class)
    public ResponseEntity<ApiResponse<Void>> handleSensitiveWordException(SensitiveWordException e) {
        log.warn("敏感词拦截, code={}", e.getCode());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimitException(RateLimitException e) {
        log.warn("限流触发, code={}", e.getCode());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(ChatException.class)
    public ResponseEntity<ApiResponse<Void>> handleChatException(ChatException e) {
        HttpStatus status = mapHttpStatus(e.getCode(), HttpStatus.BAD_REQUEST);
        log.warn("对话异常, code={}, message={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(status).body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(TicketException.class)
    public ResponseEntity<ApiResponse<Void>> handleTicketException(TicketException e) {
        log.warn("工单异常, code={}, message={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(ModelException.class)
    public ResponseEntity<ApiResponse<Void>> handleModelException(ModelException e) {
        log.error("大模型异常, code={}, message={}", e.getCode(), e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(e.getCode(), e.getMessage()));
    }

    // ===== 参数校验异常 =====

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidationException(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        Map<String, String> detail = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "校验失败",
                        (a, b) -> b,
                        LinkedHashMap::new));
        int code = 98_601; // LAK-98-601
        log.warn("参数校验失败, uri={}, detail={}", request.getRequestURI(), detail);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(code, "参数校验失败", detail));
    }

    // ===== 未兜底的异常 — 统一 500 =====

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknownException(Exception e) {
        log.error("未捕获异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(99_500, "系统内部错误"));
    }

    private HttpStatus mapHttpStatus(int code, HttpStatus defaultStatus) {
        if (code == 99_501 || code == 99_502) return HttpStatus.SERVICE_UNAVAILABLE;
        if (code >= 99_000) return HttpStatus.INTERNAL_SERVER_ERROR;
        return defaultStatus;
    }
}
