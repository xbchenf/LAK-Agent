package com.lak.ai.service.agent.sub;

import com.lak.ai.enums.IntentType;
import com.lak.ai.model.bo.AgentRequest;
import com.lak.ai.model.bo.AgentResponse;
import com.lak.ai.service.agent.SubAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 投诉建议 Agent — 引导用户到工单页面填写，不在此处做多轮采集。
 */
@Slf4j
@Component
public class ComplaintAgent implements SubAgent {

    private static final String GUIDE_TEMPLATE = """
            如需投诉或建议，请点击 <a href="/tickets">投诉建议</a> 页面填写工单，我们会尽快处理。\n\n您也可以直接在工单页面查看历史工单的处理进度。

            您也可以直接在工单页面查看历史工单的处理进度。
            """;

    @Override public String getAgentId() { return "agent-complaint"; }
    @Override public String getAgentName() { return "投诉建议Agent"; }
    @Override public IntentType[] getSupportedIntents() { return new IntentType[]{IntentType.COMPLAINT_SUGGEST}; }

    @Override
    public AgentResponse process(AgentRequest request) {
        return AgentResponse.builder()
                .answer(GUIDE_TEMPLATE)
                .confidence(1.0)
                .intentType(IntentType.COMPLAINT_SUGGEST.name())
                .build();
    }
}
