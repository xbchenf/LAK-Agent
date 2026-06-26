package com.lak.ai.service.chat;

import com.lak.ai.enums.IntentType;
import com.lak.ai.enums.SessionStatus;
import com.lak.ai.model.bo.AgentRequest;
import com.lak.ai.model.bo.AgentResponse;
import com.lak.ai.model.bo.ContextMessage;
import com.lak.ai.model.bo.RoutingDecisionBO;
import com.lak.ai.service.agent.master.MasterAgent;
import com.lak.ai.service.agent.scheduler.SubAgentScheduler;
import com.lak.ai.service.agent.sub.PolicyAgentTools;
import com.lak.ai.service.agent.sub.PolicyAgentService;
import com.lak.ai.service.agent.sub.ProcedureAgentTools;
import com.lak.ai.service.agent.sub.ProcedureAgentService;
import com.lak.ai.service.chat.context.ContextWindow;
import com.lak.ai.service.chat.session.SessionManager;
import com.lak.ai.service.chat.slot.SlotFillingEngine;
import com.lak.ai.service.ticket.TicketAdapter;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ChatService {

    private final SessionManager sessionManager;
    private final ContextWindow contextWindow;
    private final MasterAgent masterAgent;
    private final SubAgentScheduler scheduler;
    private final ComplianceValidator validator;
    private final PolicyAgentService policyAgentService;
    private final PolicyAgentTools policyTools;
    private final ProcedureAgentService procedureAgentService;
    private final ProcedureAgentTools procedureTools;
    private final SlotFillingEngine slotFillingEngine;
    private final TicketAdapter ticketAdapter;

    public ChatService(SessionManager sessionManager, ContextWindow contextWindow,
            MasterAgent masterAgent, SubAgentScheduler scheduler, ComplianceValidator validator,
            PolicyAgentService policyAgentService, PolicyAgentTools policyTools,
            ProcedureAgentService procedureAgentService, ProcedureAgentTools procedureTools,
            SlotFillingEngine slotFillingEngine, TicketAdapter ticketAdapter) {
        this.sessionManager = sessionManager; this.contextWindow = contextWindow;
        this.masterAgent = masterAgent; this.scheduler = scheduler; this.validator = validator;
        this.policyAgentService = policyAgentService; this.policyTools = policyTools;
        this.procedureAgentService = procedureAgentService; this.procedureTools = procedureTools;
        this.slotFillingEngine = slotFillingEngine;
        this.ticketAdapter = ticketAdapter;
    }

    // ==================== JSON 模式 ====================

    public ChatResult processMessage(Long userId, String sessionId, String message) {
        if (sessionId == null || sessionId.isBlank()) sessionId = sessionManager.create(userId);
        if (!sessionManager.isActive(sessionId)) return ChatResult.error("会话不存在或已关闭");

        // ── COLLECT_INFO 短路路由 ──
        SessionStatus currentStatus = sessionManager.getStatus(sessionId);
        if (currentStatus == SessionStatus.COLLECT_INFO) {
            return handleCollectInfo(sessionId, userId, message);
        }

        // ── 正常流程：意图分类 → 路由 ──
        List<ContextMessage> context = contextWindow.getContext(sessionId);
        AgentRequest request = AgentRequest.builder().sessionId(sessionId).userId(userId)
                .message(message).context(context).build();

        sessionManager.transition(sessionId, SessionStatus.INTENT_CHECK);
        RoutingDecisionBO decision = masterAgent.route(request);

        if (decision.isFallback()) {
            sessionManager.transition(sessionId, SessionStatus.FALLBACK);
            AgentResponse fallbackResponse = masterAgent.fallback(sessionId, decision);
            enforceCompliance(sessionId, fallbackResponse);
            appendToContext(sessionId, "assistant", fallbackResponse.getAnswer());
            return ChatResult.of(sessionId, fallbackResponse, decision);
        }

        sessionManager.transition(sessionId, SessionStatus.ANSWERING);
        AgentResponse response = scheduler.dispatch(decision.getIntentType(), request);
        if (response == null) {
            sessionManager.transition(sessionId, SessionStatus.FALLBACK);
            AgentResponse fallback = masterAgent.fallback(sessionId, decision);
            enforceCompliance(sessionId, fallback);
            appendToContext(sessionId, "assistant", fallback.getAnswer());
            return ChatResult.of(sessionId, fallback, decision);
        }

        // COMPLAINT_SUGGEST 进入槽位采集时，不覆写 COLLECT_INFO 状态
        boolean isCollectInfo = response.getExtra() != null
                && "COLLECT_INFO".equals(response.getExtra().get("state"));

        if (!isCollectInfo) {
            sessionManager.transition(sessionId, SessionStatus.COMPLIANCE_CHECK);
            if (!validator.validate(response, null)) {
                log.warn("合规校验未通过, sessionId={}", sessionId);
                response = AgentResponse.builder().answer("系统繁忙，请稍后重试")
                        .sources(Collections.emptyList()).confidence(0.0).build();
            }
        }

        appendToContext(sessionId, "assistant", response.getAnswer());
        return ChatResult.of(sessionId, response, decision);
    }

    // ==================== SSE 流式模式 ====================

    public SseEmitter processMessageStream(Long userId, String sessionId, String message) {
        final String sid = (sessionId == null || sessionId.isBlank())
                ? sessionManager.create(userId) : sessionId;
        final Long uid = userId;
        final String msg = message;
        SseEmitter emitter = new SseEmitter(120_000L);

        new Thread(() -> {
            try {
                if (!sessionManager.isActive(sid)) {
                    emitter.send(SseEmitter.event().name("error").data(Map.of("message","会话不存在")));
                    emitter.complete(); return;
                }

                // ── COLLECT_INFO 短路路由 ──
                SessionStatus currentStatus = sessionManager.getStatus(sid);
                if (currentStatus == SessionStatus.COLLECT_INFO) {
                    handleCollectInfoStream(emitter, sid, uid, msg);
                    return;
                }

                // ── 正常流程 ──
                contextWindow.append(sid, "user", msg);
                sessionManager.transition(sid, SessionStatus.INTENT_CHECK);
                RoutingDecisionBO decision = masterAgent.route(request(uid, sid, msg));

                if (decision.isFallback()) {
                    AgentResponse r = masterAgent.fallback(sid, decision);
                    emitter.send(SseEmitter.event().name("message").data(r.getAnswer()));
                    emitter.send(SseEmitter.event().name("done").data(Map.of("sessionId",sid,"intentType","FALLBACK")));
                    appendToContext(sid, "assistant", r.getAnswer());
                    emitter.complete(); return;
                }

                sessionManager.transition(sid, SessionStatus.ANSWERING);
                final String intentName = decision.getIntentType().name();
                StringBuilder answerBuf = new StringBuilder();

                if (decision.getIntentType() == IntentType.POLICY_CONSULT) {
                    PolicyAgentTools.SearchResult sr = policyTools.search(msg);
                    TokenStream stream = policyAgentService.answerStream(msg, sr.formattedText());
                    stream.onPartialResponse(t -> { answerBuf.append(t); safeSend(emitter, t); })
                          .onCompleteResponse(c -> {
                              safeSendDone(emitter, sid, intentName, sr.sources());
                              emitter.complete();
                              saveStreamContext(sid, answerBuf.toString());
                          }).onError(e -> {
                              log.error("Stream error", e);
                              saveStreamContext(sid, answerBuf.toString());
                              emitter.completeWithError(e);
                          }).start();
                    return;
                }
                if (decision.getIntentType() == IntentType.PROCEDURE_GUIDE) {
                    ProcedureAgentTools.SearchResult sr = procedureTools.search(msg);
                    TokenStream stream = procedureAgentService.answerStream(msg, sr.formattedText());
                    stream.onPartialResponse(t -> { answerBuf.append(t); safeSend(emitter, t); })
                          .onCompleteResponse(c -> {
                              safeSendDone(emitter, sid, intentName, sr.sources());
                              emitter.complete();
                              saveStreamContext(sid, answerBuf.toString());
                          }).onError(e -> {
                              log.error("Stream error", e);
                              saveStreamContext(sid, answerBuf.toString());
                              emitter.completeWithError(e);
                          }).start();
                    return;
                }

                // 非 policy/procedure → JSON 一次性返回
                AgentResponse response = scheduler.dispatch(decision.getIntentType(),
                        request(uid, sid, msg));
                boolean isSlotFilling = response != null && response.getExtra() != null
                        && "COLLECT_INFO".equals(response.getExtra().get("state"));
                if (response != null && !isSlotFilling && !validator.validate(response, null)) {
                    response.setAnswer("系统繁忙，请稍后重试");
                }
                emitter.send(SseEmitter.event().name("message").data(
                        response != null ? response.getAnswer() : "系统繁忙"));
                Map<String, Object> donePayload = new LinkedHashMap<>();
                donePayload.put("sessionId", sid);
                donePayload.put("intentType", decision.getIntentType().name());
                if (response != null && response.getExtra() != null && !response.getExtra().isEmpty()) {
                    donePayload.put("extra", response.getExtra());
                }
                emitter.send(SseEmitter.event().name("done").data(donePayload));
                if (response != null) appendToContext(sid, "assistant", response.getAnswer());
                emitter.complete();

            } catch (Exception e) {
                log.error("SSE stream error", e);
                try { emitter.send(SseEmitter.event().name("error").data(Map.of("message","系统繁忙"))); }
                catch (IOException ignored) {}
                emitter.complete();
            }
        }).start();

        return emitter;
    }

    // ==================== COLLECT_INFO 处理 ====================

    /** JSON 模式 — 槽位处理 */
    private ChatResult handleCollectInfo(String sessionId, Long userId, String message) {
        appendToContext(sessionId, "user", message);
        SlotFillingEngine.FillingResult result = slotFillingEngine.processResponse(sessionId, message);

        return switch (result.resultType() != null ? result.resultType() : "") {
            case "fill", "modify" -> {
                appendToContext(sessionId, "assistant", result.nextPrompt());
                yield buildSlotChatResult(sessionId, result);
            }
            case "interrupted" -> {
                String hint = slotFillingEngine.getResumePrompt(sessionId);
                String interruptReason = result.interruptReason();
                if ("new_intent".equals(interruptReason) || interruptReason != null) {
                    // 用户问了新问题 → 真正路由到对应 Agent
                    try {
                        RoutingDecisionBO decision = masterAgent.route(
                                AgentRequest.builder().sessionId(sessionId).userId(userId)
                                        .message(message).context(contextWindow.getContext(sessionId)).build());
                        if (!decision.isFallback()) {
                            AgentResponse ar = scheduler.dispatch(decision.getIntentType(),
                                    AgentRequest.builder().sessionId(sessionId).userId(userId)
                                            .message(message).build());
                            if (ar != null) {
                                String answer = ar.getAnswer() + hint;
                                appendToContext(sessionId, "assistant", answer);
                                yield ChatResult.of(sessionId,
                                        AgentResponse.builder().answer(answer).confidence(ar.getConfidence())
                                                .intentType(decision.getIntentType().name()).sources(ar.getSources())
                                                .extra(Map.of("state", "COLLECT_INFO", "slotStage", "interrupted",
                                                        "interruptReason", interruptReason != null ? interruptReason : "")).build(),
                                        decision);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("COLLECT_INFO 中断路由失败, sessionId={}, error={}", sessionId, e.getMessage());
                    }
                }
                // 闲聊或其他 — 返回回钩提示
                appendToContext(sessionId, "assistant", result.nextPrompt() != null
                        ? result.nextPrompt() + hint : hint);
                yield buildSlotChatResult(sessionId, result);
            }
            case "done" -> {
                String ticketNo = ticketAdapter.createTicket(sessionId, result.slotValues());
                sessionManager.transition(sessionId, SessionStatus.TICKET_SUBMIT);
                String answer = "您的工单已创建，编号：" + ticketNo
                        + "。我们会尽快处理，请留意反馈。";
                appendToContext(sessionId, "assistant", answer);
                yield ChatResult.of(sessionId,
                        AgentResponse.builder().answer(answer).confidence(1.0)
                                .intentType("COMPLAINT_SUGGEST")
                                .extra(Map.of("ticketNo", ticketNo, "state", "TICKET_SUBMIT")).build(),
                        null);
            }
            case "cancel" -> {
                sessionManager.transition(sessionId, SessionStatus.CLOSED);
                appendToContext(sessionId, "assistant", result.nextPrompt());
                yield ChatResult.of(sessionId,
                        AgentResponse.builder().answer(result.nextPrompt()).confidence(1.0)
                                .intentType("COMPLAINT_SUGGEST")
                                .extra(Map.of("state", "CLOSED")).build(),
                        null);
            }
            case "redirect" -> {
                // 引导转手工填写 — extra 中携带已填数据，前端据此决定是否跳转
                Map<String, String> prefill = result.slotValues() != null
                        ? new java.util.LinkedHashMap<>(result.slotValues()) : Map.of();
                String answer = (result.nextPrompt() != null ? result.nextPrompt() : "")
                        + "\n\n请在 <a href=\"/tickets\">投诉建议</a> 页面继续填写，已填信息将自动带入。";
                appendToContext(sessionId, "assistant", answer);
                yield ChatResult.of(sessionId,
                        AgentResponse.builder().answer(answer).confidence(1.0)
                                .intentType("COMPLAINT_SUGGEST")
                                .extra(Map.of("state", "COLLECT_INFO", "slotStage", "redirect",
                                        "redirectToManual", true,
                                        "prefilledSlots", prefill))
                                .build(),
                        null);
            }
            default -> {
                appendToContext(sessionId, "assistant",
                        result.nextPrompt() != null ? result.nextPrompt() : "请继续填写。");
                yield buildSlotChatResult(sessionId, result);
            }
        };
    }

    /** SSE 模式 — 槽位处理 */
    private void handleCollectInfoStream(SseEmitter emitter, String sid, Long uid, String msg)
            throws IOException {
        contextWindow.append(sid, "user", msg);
        SlotFillingEngine.FillingResult result = slotFillingEngine.processResponse(sid, msg);

        String intentName = "COMPLAINT_SUGGEST";
        switch (result.resultType() != null ? result.resultType() : "") {
            case "fill", "modify" -> {
                emitter.send(SseEmitter.event().name("message")
                        .data(result.nextPrompt() != null ? result.nextPrompt() : ""));
                emitter.send(SseEmitter.event().name("done").data(Map.of(
                        "sessionId", sid, "intentType", intentName,
                        "state", "COLLECT_INFO", "slotStage", result.resultType())));
                appendToContext(sid, "assistant", result.nextPrompt());
            }
            case "interrupted" -> {
                String hint = slotFillingEngine.getResumePrompt(sid);
                String interruptReason = result.interruptReason();
                String answer;
                Map<String, Object> donePayload = new LinkedHashMap<>();
                donePayload.put("sessionId", sid);
                donePayload.put("intentType", intentName);
                donePayload.put("state", "COLLECT_INFO");
                donePayload.put("slotStage", "interrupted");
                donePayload.put("interruptReason", interruptReason != null ? interruptReason : "");

                if ("new_intent".equals(interruptReason) || interruptReason != null) {
                    // 用户问了新问题 → 真正路由到对应 Agent
                    try {
                        RoutingDecisionBO decision = masterAgent.route(request(uid, sid, msg));
                        if (!decision.isFallback()) {
                            AgentResponse ar = scheduler.dispatch(decision.getIntentType(), request(uid, sid, msg));
                            if (ar != null) {
                                answer = ar.getAnswer() + hint;
                                donePayload.put("intentType", decision.getIntentType().name());
                                if (ar.getSources() != null) donePayload.put("sources", ar.getSources());
                                emitter.send(SseEmitter.event().name("message").data(answer));
                                emitter.send(SseEmitter.event().name("done").data(donePayload));
                                appendToContext(sid, "assistant", answer);
                                emitter.complete();
                                return;
                            }
                        }
                    } catch (Exception e) {
                        log.warn("COLLECT_INFO SSE 中断路由失败, sid={}, error={}", sid, e.getMessage());
                    }
                }
                answer = result.nextPrompt() != null ? result.nextPrompt() + hint : hint;
                emitter.send(SseEmitter.event().name("message").data(answer));
                emitter.send(SseEmitter.event().name("done").data(donePayload));
                appendToContext(sid, "assistant", answer);
            }
            case "done" -> {
                String ticketNo = ticketAdapter.createTicket(sid, result.slotValues());
                sessionManager.transition(sid, SessionStatus.TICKET_SUBMIT);
                String answer = "您的工单已创建，编号：" + ticketNo
                        + "。我们会尽快处理，请留意反馈。";
                emitter.send(SseEmitter.event().name("message").data(answer));
                emitter.send(SseEmitter.event().name("done").data(Map.of(
                        "sessionId", sid, "intentType", intentName,
                        "ticketNo", ticketNo, "state", "TICKET_SUBMIT")));
                appendToContext(sid, "assistant", answer);
            }
            case "cancel" -> {
                sessionManager.transition(sid, SessionStatus.CLOSED);
                String answer = result.nextPrompt() != null ? result.nextPrompt() : "已取消。";
                emitter.send(SseEmitter.event().name("message").data(answer));
                emitter.send(SseEmitter.event().name("done").data(Map.of(
                        "sessionId", sid, "intentType", intentName, "state", "CLOSED")));
                appendToContext(sid, "assistant", answer);
            }
            case "redirect" -> {
                Map<String, String> prefill = result.slotValues() != null
                        ? new LinkedHashMap<>(result.slotValues()) : Map.of();
                String answer = (result.nextPrompt() != null ? result.nextPrompt() : "")
                        + "\n\n请在 <a href=\"/tickets\">投诉建议</a> 页面继续填写，已填信息将自动带入。";
                Map<String, Object> rPayload = new LinkedHashMap<>();
                rPayload.put("sessionId", sid);
                rPayload.put("intentType", intentName);
                rPayload.put("state", "COLLECT_INFO");
                rPayload.put("slotStage", "redirect");
                rPayload.put("redirectToManual", true);
                rPayload.put("prefilledSlots", prefill);
                emitter.send(SseEmitter.event().name("message").data(answer));
                emitter.send(SseEmitter.event().name("done").data(rPayload));
                appendToContext(sid, "assistant", answer);
            }
            default -> {
                emitter.send(SseEmitter.event().name("message")
                        .data(result.nextPrompt() != null ? result.nextPrompt() : "请继续填写。"));
                emitter.send(SseEmitter.event().name("done").data(Map.of(
                        "sessionId", sid, "intentType", intentName,
                        "state", "COLLECT_INFO")));
                appendToContext(sid, "assistant", result.nextPrompt());
            }
        }
        emitter.complete();
    }

    /** 构建槽位处理结果的 ChatResult */
    private ChatResult buildSlotChatResult(String sessionId,
                                            SlotFillingEngine.FillingResult result) {
        return ChatResult.of(sessionId,
                AgentResponse.builder()
                        .answer(result.nextPrompt() != null ? result.nextPrompt() : "")
                        .confidence(1.0)
                        .intentType("COMPLAINT_SUGGEST")
                        .extra(Map.of(
                                "state", "COLLECT_INFO",
                                "slotStage", result.resultType() != null ? result.resultType() : "fill",
                                "interruptReason", result.interruptReason() != null
                                        ? result.interruptReason() : ""
                        ))
                        .build(),
                null);
    }

    // ==================== 辅助方法 ====================

    private void saveStreamContext(String sid, String fullAnswer) {
        appendToContext(sid, "assistant", fullAnswer);
        AgentResponse tr = AgentResponse.builder().answer(fullAnswer).build();
        if (!validator.validate(tr, null))
            log.warn("SSE流式答复合规校验未通过, sessionId={}", sid);
    }

    private void safeSend(SseEmitter e, String data) {
        try { e.send(SseEmitter.event().name("message").data(data)); } catch (IOException ex) { /* ignore */ }
    }
    private void safeSendDone(SseEmitter e, String sid, String intent,
                               List<Map<String, Object>> sources) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sessionId", sid);
        payload.put("intentType", intent);
        if (sources != null && !sources.isEmpty()) payload.put("sources", sources);
        try { e.send(SseEmitter.event().name("done").data(payload)); }
        catch (IOException ex) { /* ignore */ }
    }

    private AgentRequest request(Long userId, String sessionId, String message) {
        return AgentRequest.builder().sessionId(sessionId).userId(userId).message(message)
                .context(contextWindow.getContext(sessionId)).build();
    }

    public SessionManager getSessionManager() { return sessionManager; }

    private void enforceCompliance(String sessionId, AgentResponse response) {
        if (!validator.validate(response, null)) {
            log.warn("合规校验未通过, 已替换为安全兜底答复, sessionId={}", sessionId);
            response.setAnswer("系统繁忙，请稍后重试");
            response.setConfidence(0.0); response.setSources(Collections.emptyList());
        }
    }

    private void appendToContext(String sessionId, String role, String content) {
        if (content != null && !content.isBlank()) contextWindow.append(sessionId, role, content);
    }

    // ===== Inner types =====

    public record ChatResult(String sessionId, AgentResponse response, RoutingDecisionBO decision,
                              boolean error, String errorMessage) {
        public static ChatResult of(String sessionId, AgentResponse response, RoutingDecisionBO decision) {
            return new ChatResult(sessionId, response, decision, false, null);
        }
        public static ChatResult error(String errorMessage) {
            return new ChatResult(null, null, null, true, errorMessage);
        }
    }
}
