package com.lak.ai.enums;

/**
 * 会话状态机 — 8 个状态。
 * <p>
 * NEW → INTENT_CHECK → ANSWERING | COLLECT_INFO | FALLBACK → COMPLIANCE_CHECK | TICKET_SUBMIT → CLOSED
 */
public enum SessionStatus {

    /** 会话刚创建 */
    NEW,

    /** 主Agent 意图识别中 */
    INTENT_CHECK,

    /** RAG + 大模型生成答复中（政策/指引） */
    ANSWERING,

    /** 多轮信息采集中（投诉 Slot-Filling） */
    COLLECT_INFO,

    /** 低置信度兜底 */
    FALLBACK,

    /** 合规校验中 */
    COMPLIANCE_CHECK,

    /** 工单已提交 */
    TICKET_SUBMIT,

    /** 会话关闭（用户主动或超时） */
    CLOSED,

    /** 等待人工坐席接入（已入队列） */
    WAITING_OPERATOR,

    /** 人工坐席处理中 */
    HUMAN_HANDLING
}
