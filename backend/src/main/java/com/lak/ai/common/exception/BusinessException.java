package com.lak.ai.common.exception;

import lombok.Getter;

/**
 * 业务异常基类 — 所有自定义异常继承此类。
 * <p>
 * 遵循阿里巴巴Java开发手册 — 不在业务代码中直接使用 RuntimeException，使用此体系表达业务语义。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
