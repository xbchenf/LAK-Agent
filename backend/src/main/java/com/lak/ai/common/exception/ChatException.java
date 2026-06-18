package com.lak.ai.common.exception;

/**
 * 对话异常 — 敏感词拦截 / 限流 / 会话不存在 / 大模型超时。
 * <p>
 * 对应错误码: LAK-02-xxx
 */
public class ChatException extends BusinessException {

    public ChatException(int code, String message) {
        super(code, message);
    }
}
