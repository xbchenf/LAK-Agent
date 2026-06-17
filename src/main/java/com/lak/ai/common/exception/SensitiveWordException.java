package com.lak.ai.common.exception;

/**
 * 敏感词拦截异常 — 命中敏感词，消息被拒绝。
 * <p>
 * 注意：此异常的 message 不返回命中的具体词汇，仅返回 "消息包含敏感内容，请调整后重试"。
 */
public class SensitiveWordException extends BusinessException {

    public SensitiveWordException(int code, String message) {
        super(code, message);
    }
}
