package com.lak.ai.enums;

/**
 * 工单状态。
 */
public enum TicketStatus {

    /** 待处理 */
    PENDING,

    /** 处理中 */
    PROCESSING,

    /** 已完成 */
    COMPLETED,

    /** 创建失败（外部系统异常） */
    FAILED
}
