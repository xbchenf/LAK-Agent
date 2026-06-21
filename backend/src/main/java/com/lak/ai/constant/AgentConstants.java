package com.lak.ai.constant;

/**
 * Agent 相关常量 — 子Agent ID、置信度阈值等。
 */
public final class AgentConstants {

    private AgentConstants() {}

    /** 子Agent ID */
    public static final String AGENT_POLICY = "agent-policy";
    public static final String AGENT_PROCEDURE = "agent-procedure";
    public static final String AGENT_COMPLAINT = "agent-complaint";

    /** 置信度阈值（来自架构设计） */
    public static final double CONFIDENCE_THRESHOLD_HIGH = 0.7;
    public static final double CONFIDENCE_THRESHOLD_LOW = 0.5;

    /** Self-Consistency 投票次数 */
    public static final int VOTING_ROUNDS = 3;
}
