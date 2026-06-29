package com.lak.ai.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lak.ai.service.chat.session.SessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 人工对话 WebSocket — 双向实时通讯。
 * <p>
 * 连接路径: /ws/chat?sessionId=xxx&role=user|operator&token=jwt
 * <p>
 * 用户端和坐席端各自建立 WebSocket 连接，消息通过此 Handler 相互转发。
 */
@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** sessionId:user → WebSocket */
    private final Map<String, WebSocketSession> userSockets = new ConcurrentHashMap<>();
    /** sessionId:operator → WebSocket */
    private final Map<String, WebSocketSession> operatorSockets = new ConcurrentHashMap<>();

    public ChatWebSocketHandler(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = getParam(session, "sessionId");
        String role = getParam(session, "role");
        if (sessionId == null || role == null) {
            closeSilently(session);
            return;
        }
        if ("user".equals(role)) {
            userSockets.put(sessionId, session);
            log.info("用户 WebSocket 已连接, sessionId={}", sessionId);
        } else if ("operator".equals(role)) {
            operatorSockets.put(sessionId, session);
            log.info("坐席 WebSocket 已连接, sessionId={}", sessionId);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            Map<String, Object> msg = objectMapper.readValue(message.getPayload(), Map.class);
            String sessionId = getParam(session, "sessionId");
            String role = getParam(session, "role");
            if (sessionId == null || role == null) return;

            // 用户消息 → 转发给坐席
            if ("user".equals(role)) {
                WebSocketSession op = operatorSockets.get(sessionId);
                if (op != null && op.isOpen()) {
                    op.sendMessage(new TextMessage(message.getPayload()));
                }
            }
            // 坐席消息 → 转发给用户
            else if ("operator".equals(role)) {
                WebSocketSession user = userSockets.get(sessionId);
                if (user != null && user.isOpen()) {
                    user.sendMessage(new TextMessage(message.getPayload()));
                }
            }
        } catch (Exception e) {
            log.warn("WebSocket 消息处理失败", e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = getParam(session, "sessionId");
        String role = getParam(session, "role");
        if (sessionId != null && "user".equals(role)) {
            userSockets.remove(sessionId);
            log.info("用户 WebSocket 断开, sessionId={}", sessionId);
        } else if (sessionId != null && "operator".equals(role)) {
            operatorSockets.remove(sessionId);
            log.info("坐席 WebSocket 断开, sessionId={}", sessionId);
        }
    }

    // ===== 供外部服务调用的推送方法 =====

    /** 推送消息给用户端 */
    public void pushToUser(String sessionId, Map<String, Object> data) {
        WebSocketSession user = userSockets.get(sessionId);
        sendJson(user, data);
    }

    /** 推送消息给坐席端 */
    public void pushToOperator(String sessionId, Map<String, Object> data) {
        WebSocketSession op = operatorSockets.get(sessionId);
        sendJson(op, data);
    }

    private void sendJson(WebSocketSession session, Map<String, Object> data) {
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(data)));
            } catch (Exception e) {
                log.warn("WebSocket push 失败", e);
            }
        }
    }

    private String getParam(WebSocketSession session, String key) {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) return kv[1];
        }
        return null;
    }

    private void closeSilently(WebSocketSession session) {
        try { session.close(); } catch (Exception ignored) {}
    }
}
