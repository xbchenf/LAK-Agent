package com.lak.ai.common.exception;

/**
 * 工单异常 — 创建失败 / 工单不存在。
 * <p>
 * 对应错误码: LAK-03-xxx
 */
public class TicketException extends BusinessException {

    public TicketException(int code, String message) {
        super(code, message);
    }
}
