package com.lak.ai.service.agent.sub;

import com.lak.ai.enums.IntentType;
import com.lak.ai.enums.SessionStatus;
import com.lak.ai.model.bo.AgentRequest;
import com.lak.ai.model.bo.AgentResponse;
import com.lak.ai.service.agent.SubAgent;
import com.lak.ai.service.chat.session.SessionManager;
import com.lak.ai.service.chat.slot.SlotFillingEngine;
import com.lak.ai.service.ticket.TicketAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * 投诉建议 Agent — 多轮 Slot-Filling + 工单创建。
 * <p>
 * 流程: 检查会话状态 → NEW → 开始采集 / COLLECT_INFO → 继续填充 / TICKET_SUBMIT → 创建工单。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComplaintAgent implements SubAgent {

    private final SessionManager sessionManager;
    private final SlotFillingEngine slotFillingEngine;
    private final TicketAdapter ticketAdapter;

    @Override
    public String getAgentId() {
        return "agent-complaint";
    }

    @Override
    public String getAgentName() {
        return "投诉建议Agent";
    }

    @Override
    public IntentType[] getSupportedIntents() {
        return new IntentType[]{IntentType.COMPLAINT_SUGGEST};
    }

    @Override
    public AgentResponse process(AgentRequest request) {
        String sessionId = request.getSessionId();
        String userMessage = request.getMessage();

        try {
            // 检查会话状态
            SessionStatus status = sessionManager.getStatus(sessionId);
            if (status == null) {
                // 新会话 → 开始 Slot-Filling
                return startNewComplaint(sessionId);
            }

            return switch (status) {
                case NEW, INTENT_CHECK, ANSWERING, COMPLIANCE_CHECK, FALLBACK ->
                        startNewComplaint(sessionId);
                case COLLECT_INFO -> continueFilling(sessionId, userMessage);
                case TICKET_SUBMIT -> handleTicketSubmit(sessionId, userMessage);
                case CLOSED -> alreadyClosed(sessionId);
            };
        } catch (Exception e) {
            log.error("投诉Agent处理失败, sessionId={}", sessionId, e);
            return AgentResponse.builder()
                    .answer("系统繁忙，请稍后重试。")
                    .confidence(0.0)
                    .intentType(IntentType.COMPLAINT_SUGGEST.name())
                    .build();
        }
    }

    private AgentResponse startNewComplaint(String sessionId) {
        String prompt = slotFillingEngine.startFilling(sessionId);
        return AgentResponse.builder()
                .answer(prompt)
                .confidence(0.9)
                .intentType(IntentType.COMPLAINT_SUGGEST.name())
                .build();
    }

    private AgentResponse continueFilling(String sessionId, String userMessage) {
        SlotFillingEngine.FillingResult result = slotFillingEngine.processResponse(sessionId, userMessage);

        if (result.done()) {
            if (result.slotValues() != null && !result.slotValues().isEmpty()) {
                // 所有槽位填充完成 → 创建工单（Step 8 实现 TicketAdapter）
                Map<String, String> slots = result.slotValues();
                // 创建工单
                String ticketNo = ticketAdapter.createTicket(sessionId, slots);
                log.info("投诉信息采集完成并创建工单, sessionId={}, ticketNo={}", sessionId, ticketNo);
                return AgentResponse.builder()
                        .answer("您的诉求已受理，工单编号：" + ticketNo + "。我们会尽快处理，请留意您提供的联系方式。如需查询进度，可在工单查询页面输入工单编号。")
                        .confidence(1.0)
                        .intentType(IntentType.COMPLAINT_SUGGEST.name())
                        .extra(Map.of("slotValues", slots, "ticketNo", ticketNo))
                        .build();
            }
            // 超时或其他终止
            return AgentResponse.builder()
                    .answer(result.nextPrompt() != null ? result.nextPrompt()
                            : "信息采集已结束，如需帮助请联系人工客服。")
                    .confidence(0.5)
                    .intentType(IntentType.COMPLAINT_SUGGEST.name())
                    .build();
        }

        if (result.needsRetry()) {
            return AgentResponse.builder()
                    .answer(result.retryMessage())
                    .confidence(0.8)
                    .intentType(IntentType.COMPLAINT_SUGGEST.name())
                    .build();
        }

        // 下一个槽位的追问
        return AgentResponse.builder()
                .answer(result.nextPrompt())
                .confidence(0.85)
                .intentType(IntentType.COMPLAINT_SUGGEST.name())
                .build();
    }

    private AgentResponse handleTicketSubmit(String sessionId, String userMessage) {
        return AgentResponse.builder()
                .answer("您的诉求已受理，如需查询进度，请提供工单编号。")
                .confidence(1.0)
                .intentType(IntentType.COMPLAINT_SUGGEST.name())
                .build();
    }

    private AgentResponse alreadyClosed(String sessionId) {
        return AgentResponse.builder()
                .answer("该会话已经结束。如需新的帮助，请重新发起咨询。")
                .confidence(0.0)
                .intentType(IntentType.COMPLAINT_SUGGEST.name())
                .sources(Collections.emptyList())
                .build();
    }
}
