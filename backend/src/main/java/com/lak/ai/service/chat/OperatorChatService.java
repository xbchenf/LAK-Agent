package com.lak.ai.service.chat;

import com.lak.ai.enums.SessionStatus;
import com.lak.ai.model.bo.AgentResponse;
import com.lak.ai.model.bo.ContextMessage;
import com.lak.ai.service.chat.context.ContextWindow;
import com.lak.ai.service.chat.session.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map;

/**
 * 坐席对话服务 — 接管/消息/关闭人工会话。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperatorChatService {

    private final SessionManager sessionManager;
    private final ContextWindow contextWindow;
    private final MessagePushService pushService;
    private final com.lak.ai.service.audit.OperatorAuditLogger auditLogger;

    /**
     * 坐席接管会话。
     */
    public TakeoverResult takeover(String sessionId, Long operatorId) {
        var status = sessionManager.getStatus(sessionId);
        // 允许重复进入已接管的会话
        if (status == SessionStatus.HUMAN_HANDLING) {
            String storedOpId = (String) sessionManager.getAll(sessionId).getOrDefault("operatorId", "");
            if (String.valueOf(operatorId).equals(storedOpId)) {
                List<ContextMessage> messages = sessionManager.getMessages(sessionId);
                return new TakeoverResult(sessionId, messages);
            }
            throw new RuntimeException("会话已被其他坐席接入");
        }
        if (status != SessionStatus.WAITING_OPERATOR) {
            throw new RuntimeException("会话不在等待队列中");
        }
        // 标记已接管
        sessionManager.transition(sessionId, SessionStatus.HUMAN_HANDLING);
        sessionManager.setField(sessionId, "operatorId", String.valueOf(operatorId));
        sessionManager.setField(sessionId, "takeoverAt", LocalDateTime.now().toString());
        sessionManager.dequeueWaiting(sessionId);
        sessionManager.addToOperatorSessions(sessionId, operatorId);

        List<ContextMessage> messages = sessionManager.getMessages(sessionId);

        // SSE 通知用户端
        pushService.pushToUser(sessionId, "message",
                Map.of("role", "operator", "content", "坐席已接入，正在为您服务。"));
        auditLogger.log(sessionId, operatorId, "SESSION_TAKEOVER_OPERATOR", "Operator took over");
        log.info("坐席接管会话, sessionId={}, operatorId={}", sessionId, operatorId);
        return new TakeoverResult(sessionId, messages);
    }

    /**
     * 坐席发送消息 → 追加到会话上下文。
     */
    public void sendMessage(String sessionId, Long operatorId, String content) {
        var status = sessionManager.getStatus(sessionId);
        if (status != SessionStatus.HUMAN_HANDLING) {
            throw new RuntimeException("会话未被接管");
        }
        verifyOperator(sessionId, operatorId);
        contextWindow.append(sessionId, "operator", content);
        // SSE 推送给用户端
        pushService.pushToUser(sessionId, "message",
                Map.of("role", "operator", "content", content));
        auditLogger.log(sessionId, operatorId, "OPERATOR_MESSAGE_SEND", "len=" + content.length());

        log.info("坐席发送消息, sessionId={}, operatorId={}, len={}", sessionId, operatorId, content.length());
    }

    /**
     * 坐席关闭会话。
     */
    public void closeSession(String sessionId, Long operatorId, String notes) {
        verifyOperator(sessionId, operatorId);
        if (notes != null && !notes.isBlank()) {
            sessionManager.setField(sessionId, "operatorNotes", notes);
        }
        sessionManager.transition(sessionId, SessionStatus.CLOSED);
        sessionManager.dequeueWaiting(sessionId);
        sessionManager.removeFromOperatorSessions(sessionId, operatorId);
        // SSE 通知用户端
        pushService.pushToUser(sessionId, "close",
                Map.of("type", "close", "message", "会话已结束"));
        auditLogger.log(sessionId, operatorId, "SESSION_CLOSE_OPERATOR", notes != null ? notes : "");
        log.info("坐席关闭会话, sessionId={}, operatorId={}", sessionId, operatorId);
    }

    /** IDOR 防护：验证操作者是会话的接管人 */
    private void verifyOperator(String sessionId, Long operatorId) {
        String storedOpId = (String) sessionManager.getAll(sessionId).getOrDefault("operatorId", "");
        if (storedOpId.isBlank() || !String.valueOf(operatorId).equals(storedOpId)) {
            throw new RuntimeException("无权操作此会话");
        }
    }

    /**
     * 获取会话的坐席端视图（对话历史 + 摘要）。
     */
    public List<ContextMessage> getMessages(String sessionId, Long operatorId) {
        verifyOperator(sessionId, operatorId);
        return sessionManager.getMessages(sessionId);
    }

    // ===== 用户端轮询：获取坐席新消息 =====

    /**
     * 获取该会话中自上次查询以来的坐席消息。
     */
    public List<ContextMessage> getNewOperatorMessages(String sessionId, int sinceIndex, Long operatorId) {
        verifyOperator(sessionId, operatorId);
        List<ContextMessage> all = sessionManager.getMessages(sessionId);
        if (all.size() <= sinceIndex) return Collections.emptyList();
        return all.subList(sinceIndex, all.size());
    }

    public record TakeoverResult(String sessionId, List<ContextMessage> history) {}
}
