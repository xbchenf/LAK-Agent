package com.lak.ai.common.exception;

/**
 * 认证异常 — Token 无效 / 未登录 / 权限不足。
 * <p>
 * 对应错误码: LAK-01-xxx
 */
public class AuthException extends BusinessException {

    public AuthException(int code, String message) {
        super(code, message);
    }
}
