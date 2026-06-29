package com.lak.ai.enums;

import lombok.Getter;

/**
 * 用户意图类别 — 主Agent 意图识别结果。
 */
@Getter
public enum IntentType {

    /** 政策法规咨询 */
    POLICY_CONSULT("政策咨询", "agent-policy"),

    /** 办事流程指引 */
    PROCEDURE_GUIDE("办事指引", "agent-procedure"),

    /** 投诉建议 */
    COMPLAINT_SUGGEST("投诉建议", "agent-complaint"),

    /** 请求转人工坐席 */
    REQUEST_HUMAN("请求转人工", null),

    /** 闲聊 / 无关话题 */
    CHITCHAT("闲聊", null),

    /** 无法识别 */
    UNKNOWN("未知", null);

    private final String displayName;
    /** 对应的子Agent ID，null 表示由兜底处理 */
    private final String targetAgentId;

    IntentType(String displayName, String targetAgentId) {
        this.displayName = displayName;
        this.targetAgentId = targetAgentId;
    }

    /**
     * 是否需要兜底处理。
     */
    public boolean requiresFallback() {
        return targetAgentId == null;
    }
}
