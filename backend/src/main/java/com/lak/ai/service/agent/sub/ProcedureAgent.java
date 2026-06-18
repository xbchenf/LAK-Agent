package com.lak.ai.service.agent.sub;

import com.lak.ai.enums.IntentType;
import com.lak.ai.model.bo.AgentRequest;
import com.lak.ai.model.bo.AgentResponse;
import com.lak.ai.service.agent.SubAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 办事指引 Agent — 强制两步：代码检索 + LLM 生成。
 */
@Slf4j
@Component
public class ProcedureAgent implements SubAgent {

    private final ProcedureAgentService agentService;
    private final ProcedureAgentTools tools;

    public ProcedureAgent(ProcedureAgentService agentService, ProcedureAgentTools tools) {
        this.agentService = agentService;
        this.tools = tools;
    }

    @Override public String getAgentId() { return "agent-procedure"; }
    @Override public String getAgentName() { return "办事指引Agent"; }
    @Override public IntentType[] getSupportedIntents() { return new IntentType[]{IntentType.PROCEDURE_GUIDE}; }

    @Override
    public AgentResponse process(AgentRequest request) {
        try {
            // Step 1: 强制检索（代码层面，不依赖 LLM）
            String docs = tools.search(request.getMessage());
            // Step 2: LLM 基于检索资料生成（检索结果注入 Prompt）
            String answer = agentService.answer(request.getMessage(), docs);
            return AgentResponse.builder().answer(answer).confidence(0.9)
                    .intentType(IntentType.PROCEDURE_GUIDE.name()).build();
        } catch (Exception e) {
            log.error("ProcedureAgent 处理失败", e);
            return AgentResponse.builder().answer("系统繁忙，请稍后重试。").confidence(0.0)
                    .intentType(IntentType.PROCEDURE_GUIDE.name()).build();
        }
    }
}
