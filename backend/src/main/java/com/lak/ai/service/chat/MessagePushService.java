package com.lak.ai.service.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 消息推送服务 — 委托给 ChatWebSocketHandler。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessagePushService {

    private final ChatWebSocketHandler chatHandler;

    public void pushToOperator(String sessionId, String role, String content) {
        chatHandler.pushToOperator(sessionId, Map.of("role", role, "content", content));
    }

    public void pushToUser(String sessionId, String event, Object data) {
        if (data instanceof Map) {
            chatHandler.pushToUser(sessionId, (Map<String, Object>) data);
        }
    }

    public void registerUser(String sessionId, Object emitter) { /* WebSocket 自动管理 */ }
    public void registerOperator(String sessionId, Object emitter) { /* WebSocket 自动管理 */ }
    public boolean isUserOnline(String sessionId) { return true; }
}
