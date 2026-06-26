package com.lak.ai.common.exception;

/**
 * 知识库异常 — 文档上传/解析/状态管理相关异常。
 * <p>
 * 错误码段: LAK-04-xxx (code 范围 4_401 ~ 4_406)。
 */
public class KnowledgeException extends BusinessException {

    public KnowledgeException(int code, String message) {
        super(code, message);
    }

    public KnowledgeException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
