package com.lak.ai.service.chat;

import com.lak.ai.enums.IntentType;
import com.lak.ai.enums.SessionStatus;
import com.lak.ai.model.bo.AgentRequest;
import com.lak.ai.model.bo.AgentResponse;
import com.lak.ai.model.bo.ContextMessage;
import com.lak.ai.model.bo.RoutingDecisionBO;
import com.lak.ai.service.agent.master.FallbackHandler;
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
    private final FallbackHandler fallbackHandler;
    private final MessagePushService pushService;

    /** 兜底计数器：sessionId → 连续兜底次数 */
    private final java.util.Map<String, Integer> fallbackCount = new java.util.concurrent.ConcurrentHashMap<>();

    public ChatService(SessionManager sessionManager, ContextWindow contextWindow,
            MasterAgent masterAgent, SubAgentScheduler scheduler, ComplianceValidator validator,
            PolicyAgentService policyAgentService, PolicyAgentTools policyTools,
            ProcedureAgentService procedureAgentService, ProcedureAgentTools procedureTools,
            SlotFillingEngine slotFillingEngine, TicketAdapter ticketAdapter,
            FallbackHandler fallbackHandler, MessagePushService pushService) {
        this.sessionManager = sessionManager; this.contextWindow = contextWindow;
        this.masterAgent = masterAgent; this.scheduler = scheduler; this.validator = validator;
        this.policyAgentService = policyAgentService; this.policyTools = policyTools;
        this.procedureAgentService = procedureAgentService; this.procedureTools = procedureTools;
        this.slotFillingEngine = slotFillingEngine;
        this.ticketAdapter = ticketAdapter;
        this.fallbackHandler = fallbackHandler;
        this.pushService = pushService;
    }

    // ==================== JSON 模式 ====================

    public ChatResult processMessage(Long userId, String sessionId, String message) {
        if (sessionId == null || sessionId.isBlank()) sessionId = sessionManager.create(userId);
        if (!sessionManager.isActive(sessionId)) {
            sessionId = sessionManager.create(userId); // 自动创建新会话
        }

        // ── COLLECT_INFO 短路路由 ──
        SessionStatus currentStatus = sessionManager.getStatus(sessionId);
        if (currentStatus == SessionStatus.COLLECT_INFO) {
            return handleCollectInfo(sessionId, userId, message);
        }

        // ── 人工坐席处理中 → 消息存历史，不调AI ──
        if (currentStatus == SessionStatus.HUMAN_HANDLING) {
            appendToContext(sessionId, "user", message);
            pushService.pushToOperator(sessionId, "user", message);
            return ChatResult.of(sessionId,
                    AgentResponse.builder()
                            .answer("")  // 不返回答复，由坐席回复
                            .confidence(1.0).intentType("HUMAN_HANDLING")
                            .extra(Map.of("state", "HUMAN_HANDLING")).build(),
                    null);
        }

        // ── 已在人工排队 → 返回排队状态 ──
        if (currentStatus == SessionStatus.WAITING_OPERATOR) {
            int pos = sessionManager.getQueuePosition(sessionId);
            return ChatResult.of(sessionId,
                    AgentResponse.builder()
                            .answer("您已在人工排队中，当前排在第 " + pos + " 位。如情况紧急，请拨打 12345 政务服务热线。")
                            .confidence(1.0).intentType("WAITING_OPERATOR")
                            .extra(Map.of("state", "WAITING_OPERATOR", "queuePosition", pos)).build(),
                    null);
        }

        // ── 待确认转人工 → 判断用户是确认还是拒绝 ──
        if (sessionManager.hasPendingHandoff(sessionId)) {
            return handleHandoffConfirmation(sessionId, message);
        }

        // ── 正常流程：意图分类 → 路由 ──
        List<ContextMessage> context = contextWindow.getContext(sessionId);
        AgentRequest request = AgentRequest.builder().sessionId(sessionId).userId(userId)
                .message(message).context(context).build();

        sessionManager.transition(sessionId, SessionStatus.INTENT_CHECK);
        RoutingDecisionBO decision = masterAgent.route(request);

        // ── 用户主动请求转人工 → 直接入队列 ──
        if (decision.isNeedsHuman()) {
            AgentResponse handoffResp = fallbackHandler.handleDirectHandoff(sessionId,
                    decision.getConfidence(), decision.getReasoning());
            appendToContext(sessionId, "assistant", handoffResp.getAnswer());
            fallbackCount.remove(sessionId); // 重置兜底计数
            return ChatResult.of(sessionId, handoffResp, decision);
        }

        if (decision.isFallback()) {
            // 闲聊/无关话题 → 友好引导，不触发转人工
            if (decision.getIntentType() == IntentType.CHITCHAT) {
                fallbackCount.remove(sessionId);
                AgentResponse chatResp = fallbackHandler.handleChitchat();
                appendToContext(sessionId, "assistant", chatResp.getAnswer());
                return ChatResult.of(sessionId, chatResp, decision);
            }
            // 连续兜底计数
            int count = fallbackCount.merge(sessionId, 1, Integer::sum);
            if (count >= 2) {
                // T3 触发 → A 类确认
                fallbackCount.remove(sessionId);
                AgentResponse confirmResp = fallbackHandler.handleWithConfirm(sessionId,
                        decision.getConfidence(), decision.getReasoning(), "CONSECUTIVE_FALLBACK");
                appendToContext(sessionId, "assistant", confirmResp.getAnswer());
                return ChatResult.of(sessionId, confirmResp, decision);
            }
            sessionManager.transition(sessionId, SessionStatus.FALLBACK);
            AgentResponse fallbackResponse = fallbackHandler.handleLowConfidence(sessionId,
                    decision.getConfidence(), decision.getReasoning());
            enforceCompliance(sessionId, fallbackResponse);
            appendToContext(sessionId, "assistant", fallbackResponse.getAnswer());
            return ChatResult.of(sessionId, fallbackResponse, decision);
        }

        // AI 正常回答 → 重置兜底计数
        fallbackCount.remove(sessionId);
        sessionManager.transition(sessionId, SessionStatus.ANSWERING);
        AgentResponse response = scheduler.dispatch(decision.getIntentType(), request);
        if (response == null) {
            int cnt = fallbackCount.merge(sessionId, 1, Integer::sum);
            if (cnt >= 2) {
                fallbackCount.remove(sessionId);
                AgentResponse confirmResp = fallbackHandler.handleWithConfirm(sessionId,
                        decision.getConfidence(), "调度失败", "CONSECUTIVE_FALLBACK");
                appendToContext(sessionId, "assistant", confirmResp.getAnswer());
                return ChatResult.of(sessionId, confirmResp, decision);
            }
            sessionManager.transition(sessionId, SessionStatus.FALLBACK);
            AgentResponse fallback = fallbackHandler.handleLowConfidence(sessionId,
                    decision.getConfidence(), "调度失败");
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
        final String[] sidRef = { (sessionId == null || sessionId.isBlank())
                ? sessionManager.create(userId) : sessionId };
        final Long uid = userId;
        final String msg = message;
        SseEmitter emitter = new SseEmitter(120_000L);

        new Thread(() -> {
            try {
                if (!sessionManager.isActive(sidRef[0])) {
                    sidRef[0] = sessionManager.create(uid);
                }
                final String sid = sidRef[0];

                // ── COLLECT_INFO 短路路由 ──
                SessionStatus currentStatus = sessionManager.getStatus(sid);
                if (currentStatus == SessionStatus.COLLECT_INFO) {
                    handleCollectInfoStream(emitter, sid, uid, msg);
                    return;
                }

                // ── WAITING_OPERATOR / HUMAN_HANDLING / needsHuman 处理 ──
                SessionStatus sStatus = sessionManager.getStatus(sid);
                if (sStatus == SessionStatus.HUMAN_HANDLING) {
                    contextWindow.append(sid, "user", msg);
                    pushService.pushToOperator(sid, "user", msg);
                    emitter.send(SseEmitter.event().name("done").data(Map.of(
                            "sessionId", sid, "state", "HUMAN_HANDLING")));
                    emitter.complete(); return;
                }
                if (sStatus == SessionStatus.WAITING_OPERATOR) {
                    int pos = sessionManager.getQueuePosition(sid);
                    emitter.send(SseEmitter.event().name("message")
                            .data("您已在人工排队中，当前排在第 " + pos + " 位。如情况紧急，请拨打 12345 政务服务热线。"));
                    emitter.send(SseEmitter.event().name("done")
                            .data(Map.of("sessionId", sid, "state", "WAITING_OPERATOR", "queuePosition", pos)));
                    emitter.complete(); return;
                }
                if (sessionManager.hasPendingHandoff(sid)) {
                    ChatResult cr = handleHandoffConfirmation(sid, msg);
                    emitter.send(SseEmitter.event().name("message").data(cr.response().getAnswer()));
                    Map<String, Object> dp = new LinkedHashMap<>();
                    dp.put("sessionId", sid);
                    dp.put("intentType", cr.response().getIntentType());
                    if (cr.response().getExtra() != null) {
                        dp.put("extra", cr.response().getExtra());
                        // 提取 state 到顶层，供前端 WebSocket 连接判断
                        Object state = cr.response().getExtra().get("state");
                        if (state != null) dp.put("state", state);
                    }
                    emitter.send(SseEmitter.event().name("done").data(dp));
                    appendToContext(sid, "assistant", cr.response().getAnswer());
                    emitter.complete(); return;
                }

                // ── 正常流程 ──
                contextWindow.append(sid, "user", msg);
                sessionManager.transition(sid, SessionStatus.INTENT_CHECK);
                RoutingDecisionBO decision = masterAgent.route(request(uid, sid, msg));

                // ── 用户主动请求转人工 ──
                if (decision.isNeedsHuman()) {
                    AgentResponse hr = fallbackHandler.handleDirectHandoff(sid,
                            decision.getConfidence(), decision.getReasoning());
                    emitter.send(SseEmitter.event().name("message").data(hr.getAnswer()));
                    emitter.send(SseEmitter.event().name("done").data(Map.of(
                            "sessionId", sid, "intentType", "REQUEST_HUMAN",
                            "state", "WAITING_OPERATOR")));
                    appendToContext(sid, "assistant", hr.getAnswer());
                    fallbackCount.remove(sid);
                    emitter.complete(); return;
                }

                if (decision.isFallback()) {
                    AgentResponse r;
                    if (decision.getIntentType() == IntentType.CHITCHAT) {
                        fallbackCount.remove(sid);
                        r = fallbackHandler.handleChitchat();
                    } else {
                        int count = fallbackCount.merge(sid, 1, Integer::sum);
                        if (count >= 2) {
                            fallbackCount.remove(sid);
                            r = fallbackHandler.handleWithConfirm(sid,
                                    decision.getConfidence(), decision.getReasoning(), "CONSECUTIVE_FALLBACK");
                        } else {
                            sessionManager.transition(sid, SessionStatus.FALLBACK);
                            r = fallbackHandler.handleLowConfidence(sid,
                                    decision.getConfidence(), decision.getReasoning());
                        }
                    }
                    emitter.send(SseEmitter.event().name("message").data(r.getAnswer()));
                    emitter.send(SseEmitter.event().name("done").data(Map.of(
                            "sessionId", sid, "intentType", "FALLBACK",
                            "extra", r.getExtra() != null ? r.getExtra() : Map.of())));
                    appendToContext(sid, "assistant", r.getAnswer());
                    emitter.complete(); return;
                }

                fallbackCount.remove(sid);
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

    // ==================== 转人工确认处理 ====================

    /** 处理用户对"是否转人工"的确认/拒绝 */
    private ChatResult handleHandoffConfirmation(String sessionId, String message) {
        String msg = message.trim();
        // 确认关键词
        boolean confirmed = msg.equals("好的") || msg.equals("好") || msg.equals("可以")
                || msg.equals("是") || msg.equals("转") || msg.equals("嗯") || msg.equals("需要")
                || msg.contains("转人工") || msg.contains("确认") || msg.equals("yes") || msg.equals("要");
        // 拒绝关键词
        boolean rejected = msg.equals("不用") || msg.equals("不用了") || msg.equals("算了")
                || msg.equals("不") || msg.equals("不需要") || msg.equals("no") || msg.equals("不要");

        if (confirmed) {
            return ChatResult.of(sessionId, fallbackHandler.confirmHandoff(sessionId), null);
        }
        if (rejected) {
            fallbackCount.remove(sessionId);
            return ChatResult.of(sessionId, fallbackHandler.rejectHandoff(sessionId), null);
        }
        // 模糊回复 → 再次确认
        return ChatResult.of(sessionId,
                AgentResponse.builder()
                        .answer("请确认是否需要转接人工坐席？（回复[好的]确认，回复[不用了]继续自助咨询）")
                        .confidence(0.5).intentType("FALLBACK")
                        .extra(Map.of("state", "PENDING_HANDOFF")).build(),
                null);
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
