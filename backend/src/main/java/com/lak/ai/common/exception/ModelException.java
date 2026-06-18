package com.lak.ai.common.exception;

/**
 * 大模型异常 — 调用超时 / 熔断 / 不可用 / 返回异常。
 * <p>
 * 对应错误码: LAK-99-xxx
 */
public class ModelException extends BusinessException {

    public ModelException(int code, String message) {
        super(code, message);
    }

    public ModelException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
