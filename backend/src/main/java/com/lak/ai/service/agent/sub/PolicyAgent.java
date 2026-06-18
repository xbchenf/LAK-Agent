package com.lak.ai.service.agent.sub;

import com.lak.ai.enums.IntentType;
import com.lak.ai.model.bo.AgentRequest;
import com.lak.ai.model.bo.AgentResponse;
import com.lak.ai.service.agent.SubAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 政策咨询 Agent — 委托给 @AiService(PolicyAgentService) 实现 LLM 自主检索+生成。
 */
@Slf4j
@Component
public class PolicyAgent implements SubAgent {

    private final PolicyAgentService agentService;

    public PolicyAgent(PolicyAgentService agentService) { this.agentService = agentService; }

    @Override public String getAgentId() { return "agent-policy"; }
    @Override public String getAgentName() { return "政策咨询Agent"; }
    @Override public IntentType[] getSupportedIntents() { return new IntentType[]{IntentType.POLICY_CONSULT}; }

    @Override
    public AgentResponse process(AgentRequest request) {
        try {
            String answer = agentService.answer(request.getMessage());
            return AgentResponse.builder().answer(answer).confidence(0.9)
                    .intentType(IntentType.POLICY_CONSULT.name()).build();
        } catch (Exception e) {
            log.error("PolicyAgent 处理失败", e);
            return AgentResponse.builder().answer("系统繁忙，请稍后重试。").confidence(0.0)
                    .intentType(IntentType.POLICY_CONSULT.name()).build();
        }
    }
}
