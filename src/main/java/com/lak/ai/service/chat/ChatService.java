package com.lak.ai.service.chat;

import com.lak.ai.enums.SessionStatus;
import com.lak.ai.model.bo.AgentRequest;
import com.lak.ai.model.bo.AgentResponse;
import com.lak.ai.model.bo.ContextMessage;
import com.lak.ai.model.bo.RoutingDecisionBO;
import com.lak.ai.service.agent.master.MasterAgent;
import com.lak.ai.service.agent.scheduler.SubAgentScheduler;
import com.lak.ai.service.chat.context.ContextWindow;
import com.lak.ai.service.chat.session.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 对话服务 — 编排会话管理 → MasterAgent → SubAgent → 合规校验。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final SessionManager sessionManager;
    private final ContextWindow contextWindow;
    private final MasterAgent masterAgent;
    private final SubAgentScheduler scheduler;
    private final ComplianceValidator validator;

    /**
     * 处理用户消息 — 核心对话链路。
     */
    public ChatResult processMessage(Long userId, String sessionId, String message) {
        // 1. 会话管理
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = sessionManager.create(userId);
        }
        if (!sessionManager.isActive(sessionId)) {
            return ChatResult.error("会话不存在或已关闭");
        }

        // 2. 上下文
        List<ContextMessage> context = contextWindow.getContext(sessionId);
        AgentRequest request = AgentRequest.builder()
                .sessionId(sessionId)
                .userId(userId)
                .message(message)
                .context(context)
                .build();

        // 3. 主Agent 意图路由
        sessionManager.transition(sessionId, SessionStatus.INTENT_CHECK);
        RoutingDecisionBO decision = masterAgent.route(request);

        if (decision.isFallback()) {
            sessionManager.transition(sessionId, SessionStatus.FALLBACK);
            AgentResponse fallbackResponse = masterAgent.fallback(sessionId, decision);
            appendToContext(sessionId, "assistant", fallbackResponse.getAnswer());
            return ChatResult.of(sessionId, fallbackResponse, decision);
        }

        // 4. 子Agent 执行
        sessionManager.transition(sessionId, SessionStatus.ANSWERING);
        AgentResponse response = scheduler.dispatch(decision.getIntentType(), request);
        if (response == null) {
            // 无匹配 Agent → 走兜底
            sessionManager.transition(sessionId, SessionStatus.FALLBACK);
            AgentResponse fallback = masterAgent.fallback(sessionId, decision);
            appendToContext(sessionId, "assistant", fallback.getAnswer());
            return ChatResult.of(sessionId, fallback, decision);
        }

        // 5. 合规校验
        sessionManager.transition(sessionId, SessionStatus.COMPLIANCE_CHECK);
        if (!validator.validate(response, null)) {
            log.warn("合规校验未通过, sessionId={}", sessionId);
            response = AgentResponse.builder()
                    .answer("系统繁忙，请稍后重试")
                    .sources(Collections.emptyList())
                    .confidence(0.0)
                    .build();
        }

        // 6. 记录上下文
        appendToContext(sessionId, "assistant", response.getAnswer());

        return ChatResult.of(sessionId, response, decision);
    }

    private void appendToContext(String sessionId, String role, String content) {
        if (content != null && !content.isBlank()) {
            contextWindow.append(sessionId, role, content);
        }
    }

    public SessionManager getSessionManager() {
        return sessionManager;
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
