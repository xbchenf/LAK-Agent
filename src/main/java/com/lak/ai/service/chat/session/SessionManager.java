package com.lak.ai.service.chat.session;

import com.lak.ai.enums.SessionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 会话管理器 — Redis Hash 持久化，TTL 30 分钟。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionManager {

    private static final String SESSION_KEY_PREFIX = "session:";
    private static final Duration SESSION_TTL = Duration.ofSeconds(1800);

    private final StringRedisTemplate redisTemplate;

    // Redis Hash 字段名
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_INTENT_TYPE = "intentType";
    private static final String FIELD_CONFIDENCE = "confidence";
    private static final String FIELD_CREATE_TIME = "createTime";
    private static final String FIELD_LAST_ACTIVE = "lastActive";

    /**
     * 创建新会话，返回 sessionId。
     */
    public String create(Long userId) {
        String sessionId = UUID.randomUUID().toString();
        String key = sessionKey(sessionId);
        String now = LocalDateTime.now().toString();
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        hash.put(key, FIELD_USER_ID, userId != null ? userId.toString() : "");
        hash.put(key, FIELD_STATUS, SessionStatus.NEW.name());
        hash.put(key, FIELD_CREATE_TIME, now);
        hash.put(key, FIELD_LAST_ACTIVE, now);
        redisTemplate.expire(key, SESSION_TTL);
        log.debug("会话创建, sessionId={}, userId={}", sessionId, userId);
        return sessionId;
    }

    /**
     * 更新会话状态并刷新 TTL。
     */
    public void transition(String sessionId, SessionStatus newStatus) {
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        hash.put(sessionKey(sessionId), FIELD_STATUS, newStatus.name());
        touch(sessionId);
    }

    /**
     * 更新意图类型和置信度。
     */
    public void setIntent(String sessionId, String intentType, double confidence) {
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        hash.put(sessionKey(sessionId), FIELD_INTENT_TYPE, intentType);
        hash.put(sessionKey(sessionId), FIELD_CONFIDENCE, String.valueOf(confidence));
        touch(sessionId);
    }

    /**
     * 获取会话所有字段。
     */
    public Map<Object, Object> getAll(String sessionId) {
        return redisTemplate.opsForHash().entries(sessionKey(sessionId));
    }

    /**
     * 获取会话状态。
     */
    public SessionStatus getStatus(String sessionId) {
        String status = (String) redisTemplate.opsForHash().get(sessionKey(sessionId), FIELD_STATUS);
        return status != null ? SessionStatus.valueOf(status) : null;
    }

    /**
     * 检查会话是否存在且未关闭。
     */
    public boolean isActive(String sessionId) {
        SessionStatus status = getStatus(sessionId);
        return status != null && status != SessionStatus.CLOSED;
    }

    /**
     * 关闭会话。
     */
    public void close(String sessionId) {
        transition(sessionId, SessionStatus.CLOSED);
        log.debug("会话关闭, sessionId={}", sessionId);
    }

    /**
     * 刷新会话 TTL。
     */
    public void touch(String sessionId) {
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        hash.put(sessionKey(sessionId), FIELD_LAST_ACTIVE, LocalDateTime.now().toString());
        redisTemplate.expire(sessionKey(sessionId), SESSION_TTL);
    }

    private String sessionKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }
}
