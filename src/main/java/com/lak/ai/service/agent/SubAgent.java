package com.lak.ai.service.agent;

import com.lak.ai.enums.IntentType;
import com.lak.ai.model.bo.AgentRequest;
import com.lak.ai.model.bo.AgentResponse;

/**
 * 子Agent 统一接口 — 策略模式，新增 Agent 只需实现此接口并注册为 Spring Bean。
 */
public interface SubAgent {

    String getAgentId();

    String getAgentName();

    IntentType[] getSupportedIntents();

    AgentResponse process(AgentRequest request);
}
