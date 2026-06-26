package com.lak.ai.service.agent.sub;

import com.lak.ai.enums.IntentType;
import com.lak.ai.model.bo.AgentRequest;
import com.lak.ai.model.bo.AgentResponse;
import com.lak.ai.service.agent.SubAgent;
import com.lak.ai.service.chat.slot.SlotFillingEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 投诉建议 Agent — 触发 Slot-Filling 多轮对话采集，创建工单。
 * <p>
 * 首次命中 COMPLAINT_SUGGEST 意图时调用 {@link SlotFillingEngine#startFilling(String)}
 * 初始化多轮采集流程，后续轮次由 {@code ChatService} 直接调度 {@code SlotFillingEngine}。
 */
@Slf4j
@Component
public class ComplaintAgent implements SubAgent {

    private final SlotFillingEngine slotFillingEngine;

    public ComplaintAgent(SlotFillingEngine slotFillingEngine) {
        this.slotFillingEngine = slotFillingEngine;
    }

    @Override public String getAgentId() { return "agent-complaint"; }
    @Override public String getAgentName() { return "投诉建议Agent"; }
    @Override public IntentType[] getSupportedIntents() { return new IntentType[]{IntentType.COMPLAINT_SUGGEST}; }

    @Override
    public AgentResponse process(AgentRequest request) {
        // 触发 Slot-Filling，返回第一个槽位的追问话术
        String firstPrompt = slotFillingEngine.startFilling(request.getSessionId());
        log.info("ComplaintAgent 触发 Slot-Filling, sessionId={}", request.getSessionId());

        return AgentResponse.builder()
                .answer(firstPrompt)
                .confidence(1.0)
                .intentType(IntentType.COMPLAINT_SUGGEST.name())
                .extra(Map.of(
                        "state", "COLLECT_INFO",
                        "slotStage", "started"
                ))
                .build();
    }
}
