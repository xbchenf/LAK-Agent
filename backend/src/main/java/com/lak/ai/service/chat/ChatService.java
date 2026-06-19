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
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
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

    public ChatService(SessionManager sessionManager, ContextWindow contextWindow,
            MasterAgent masterAgent, SubAgentScheduler scheduler, ComplianceValidator validator,
            PolicyAgentService policyAgentService, PolicyAgentTools policyTools,
            ProcedureAgentService procedureAgentService, ProcedureAgentTools procedureTools) {
        this.sessionManager = sessionManager; this.contextWindow = contextWindow;
        this.masterAgent = masterAgent; this.scheduler = scheduler; this.validator = validator;
        this.policyAgentService = policyAgentService; this.policyTools = policyTools;
        this.procedureAgentService = procedureAgentService; this.procedureTools = procedureTools;
    }

    public ChatResult processMessage(Long userId, String sessionId, String message) {
        if (sessionId == null || sessionId.isBlank()) sessionId = sessionManager.create(userId);
        if (!sessionManager.isActive(sessionId)) return ChatResult.error("会话不存在或已关闭");

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

        sessionManager.transition(sessionId, SessionStatus.COMPLIANCE_CHECK);
        if (!validator.validate(response, null)) {
            log.warn("合规校验未通过, sessionId={}", sessionId);
            response = AgentResponse.builder().answer("系统繁忙，请稍后重试")
                    .sources(Collections.emptyList()).confidence(0.0).build();
        }

        appendToContext(sessionId, "assistant", response.getAnswer());
        return ChatResult.of(sessionId, response, decision);
    }

    /**
     * SSE 流式处理 — 仅 policy/procedure 支持流式，其他回退 JSON。
     */
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

                contextWindow.append(sid, "user", msg);
                sessionManager.transition(sid, SessionStatus.INTENT_CHECK);
                RoutingDecisionBO decision = masterAgent.route(request(uid, sid, msg));

                if (decision.isFallback()) {
                    AgentResponse r = masterAgent.fallback(sid, decision);
                    emitter.send(SseEmitter.event().name("message").data(r.getAnswer().substring(0, Math.min(100, r.getAnswer().length()))));
                    emitter.send(SseEmitter.event().name("done").data(Map.of("sessionId",sid,"intentType","FALLBACK")));
                    emitter.complete(); return;
                }

                sessionManager.transition(sid, SessionStatus.ANSWERING);
                final String intentName = decision.getIntentType().name();
                StringBuilder answerBuf = new StringBuilder();

                if (decision.getIntentType() == IntentType.POLICY_CONSULT) {
                    TokenStream stream = policyAgentService.answerStream(msg, policyTools.search(msg));
                    stream.onPartialResponse(t -> { answerBuf.append(t); safeSend(emitter, t); })
                          .onCompleteResponse(c -> { safeSendDone(emitter, sid, intentName); emitter.complete();
                              appendToContext(sid, "assistant", answerBuf.toString()); })
                          .onError(e -> emitter.completeWithError(e)).start();
                    return;
                }
                if (decision.getIntentType() == IntentType.PROCEDURE_GUIDE) {
                    TokenStream stream = procedureAgentService.answerStream(msg, procedureTools.search(msg));
                    stream.onPartialResponse(t -> { answerBuf.append(t); safeSend(emitter, t); })
                          .onCompleteResponse(c -> { safeSendDone(emitter, sid, intentName); emitter.complete();
                              appendToContext(sid, "assistant", answerBuf.toString()); })
                          .onError(e -> emitter.completeWithError(e)).start();
                    return;
                }

                // 非 policy/procedure → JSON 一次性返回（校验后再发送）
                AgentResponse response = scheduler.dispatch(decision.getIntentType(),
                        request(uid, sid, msg));
                if (response != null && !validator.validate(response, null)) {
                    response.setAnswer("系统繁忙，请稍后重试");
                }
                emitter.send(SseEmitter.event().name("message").data(
                        response != null ? response.getAnswer() : "系统繁忙"));
                emitter.send(SseEmitter.event().name("done").data(Map.of(
                        "sessionId",sid,"intentType",decision.getIntentType().name())));
                if (response != null) appendToContext(sid, "assistant", response.getAnswer());
                emitter.complete();

                // 流式路径的后置合规日志（无法撤回已发Token，但记录告警）
                String finalAnswer = answerBuf.toString();
                if (!finalAnswer.isEmpty() && validator != null) {
                    AgentResponse tempResp = AgentResponse.builder().answer(finalAnswer).build();
                    if (!validator.validate(tempResp, null)) {
                        log.warn("SSE流式答复合规校验未通过(已发送), sessionId={}", sid);
                    }
                }
            } catch (Exception e) {
                log.error("SSE stream error", e);
                try { emitter.send(SseEmitter.event().name("error").data(Map.of("message","系统繁忙"))); }
                catch (IOException ignored) {}
                emitter.complete();
            }
        }).start();

        return emitter;
    }

    private void safeSend(SseEmitter e, String data) {
        try { e.send(SseEmitter.event().name("message").data(data)); } catch (IOException ex) { /* ignore */ }
    }
    private void safeSendDone(SseEmitter e, String sid, String intent) {
        try { e.send(SseEmitter.event().name("done").data(Map.of("sessionId",sid,"intentType",intent))); }
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
            response.setConfidence(0.0); response.setSources(java.util.Collections.emptyList());
        }
    }

    private void appendToContext(String sessionId, String role, String content) {
        if (content != null && !content.isBlank()) contextWindow.append(sessionId, role, content);
    }

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
