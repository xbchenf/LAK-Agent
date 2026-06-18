package com.lak.ai.common.exception;

/**
 * 限流异常 — 请求频率超过限制。
 * <p>
 * HTTP 状态码: 429
 */
public class RateLimitException extends BusinessException {

    public RateLimitException(int code, String message) {
        super(code, message);
    }
}
