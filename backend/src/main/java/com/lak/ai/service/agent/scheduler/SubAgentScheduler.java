package com.lak.ai.service.agent.scheduler;

import com.lak.ai.enums.IntentType;
import com.lak.ai.model.bo.AgentRequest;
import com.lak.ai.model.bo.AgentResponse;
import com.lak.ai.service.agent.SubAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 子Agent 调度器 — 根据意图查找对应 SubAgent 并执行。
 */
@Slf4j
@Service
public class SubAgentScheduler {

    private final Map<IntentType, SubAgent> agentRegistry;

    public SubAgentScheduler(List<SubAgent> agents) {
        this.agentRegistry = agents.stream()
                .collect(Collectors.toMap(
                        a -> a.getSupportedIntents()[0],
                        a -> a,
                        (a, b) -> a)); // 重复 Intent → 取第一个
        log.info("子Agent注册完成, agents={}", agentRegistry.keySet());
    }

    /**
     * 根据意图分派到对应子Agent。
     * @return AgentResponse，若找不到对应的Agent则返回 null
     */
    public AgentResponse dispatch(IntentType intentType, AgentRequest request) {
        SubAgent agent = agentRegistry.get(intentType);
        if (agent == null) {
            log.warn("未找到对应Agent, intent={}", intentType);
            return null;
        }
        log.debug("调度Agent, agentId={}, sessionId={}", agent.getAgentId(), request.getSessionId());
        return agent.process(request);
    }

    public boolean hasAgent(IntentType intentType) {
        return agentRegistry.containsKey(intentType);
    }
}
