package com.lak.ai.service.chat.session;

import com.lak.ai.enums.SessionStatus;
import com.lak.ai.mapper.ChatSessionMapper;
import com.lak.ai.model.bo.ContextMessage;
import com.lak.ai.model.entity.ChatSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 会话管理器 — Redis Hash + MySQL 双写。
 */
@Slf4j
@Service
public class SessionManager {

    private static final String SESSION_KEY_PREFIX = "session:";
    private static final String QUEUE_KEY = "queue:operator_waiting";
    private static final Duration SESSION_TTL = Duration.ofSeconds(1800);

    private final StringRedisTemplate redisTemplate;
    private final ChatSessionMapper sessionMapper;

    public SessionManager(StringRedisTemplate redisTemplate, ChatSessionMapper sessionMapper) {
        this.redisTemplate = redisTemplate;
        this.sessionMapper = sessionMapper;
    }

    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_INTENT_TYPE = "intentType";
    private static final String FIELD_CONFIDENCE = "confidence";
    private static final String FIELD_CREATE_TIME = "createTime";
    private static final String FIELD_LAST_ACTIVE = "lastActive";
    private static final String FIELD_PENDING_HANDOFF = "pendingHandoff";
    private static final String FIELD_TRANSFER_REASON = "transferReason";

    // ==================== 基础操作 ====================

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
        ChatSession entity = new ChatSession();
        entity.setSessionId(sessionId);
        entity.setUserId(userId);
        entity.setStatus(SessionStatus.NEW.name());
        sessionMapper.insert(entity);
        log.debug("会话创建, sessionId={}, userId={}", sessionId, userId);
        return sessionId;
    }

    public void transition(String sessionId, SessionStatus newStatus) {
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        hash.put(sessionKey(sessionId), FIELD_STATUS, newStatus.name());
        touch(sessionId);
    }

    public void setIntent(String sessionId, String intentType, double confidence) {
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        hash.put(sessionKey(sessionId), FIELD_INTENT_TYPE, intentType);
        hash.put(sessionKey(sessionId), FIELD_CONFIDENCE, String.valueOf(confidence));
        touch(sessionId);
    }

    public Map<Object, Object> getAll(String sessionId) {
        return redisTemplate.opsForHash().entries(sessionKey(sessionId));
    }

    public SessionStatus getStatus(String sessionId) {
        String status = (String) redisTemplate.opsForHash().get(sessionKey(sessionId), FIELD_STATUS);
        return status != null ? SessionStatus.valueOf(status) : null;
    }

    public boolean isActive(String sessionId) {
        SessionStatus status = getStatus(sessionId);
        return status != null && status != SessionStatus.CLOSED;
    }

    public void close(String sessionId) {
        transition(sessionId, SessionStatus.CLOSED);
        var entity = new ChatSession();
        entity.setSessionId(sessionId);
        entity.setStatus(SessionStatus.CLOSED.name());
        sessionMapper.update(entity,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId));
        log.debug("会话关闭, sessionId={}", sessionId);
    }

    // ==================== 转人工排队 ====================

    /** 入队：返回队列位置 */
    public int enqueueWaiting(String sessionId, String reason) {
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        hash.put(sessionKey(sessionId), FIELD_TRANSFER_REASON, reason);
        hash.put(sessionKey(sessionId), FIELD_STATUS, SessionStatus.WAITING_OPERATOR.name());
        redisTemplate.opsForList().rightPush(QUEUE_KEY, sessionId);
        touch(sessionId);
        return getQueuePosition(sessionId);
    }

    /** 将指定会话从等待队列移除 */
    public void dequeueWaiting(String sessionId) {
        redisTemplate.opsForList().remove(QUEUE_KEY, 0, sessionId);
    }

    /** 坐席接管会话 — 加入坐席的活跃列表 */
    public void addToOperatorSessions(String sessionId, Long operatorId) {
        redisTemplate.opsForSet().add("operator:" + operatorId + ":sessions", sessionId);
    }

    /** 关闭会话 — 从坐席活跃列表移除 */
    public void removeFromOperatorSessions(String sessionId, Long operatorId) {
        redisTemplate.opsForSet().remove("operator:" + operatorId + ":sessions", sessionId);
    }

    /** 获取坐席当前活跃的会话 */
    public java.util.Set<String> getOperatorSessions(Long operatorId) {
        java.util.Set<String> set = redisTemplate.opsForSet().members("operator:" + operatorId + ":sessions");
        return set != null ? set : java.util.Set.of();
    }

    /** 获取排队位置（仅统计活跃等待中的会话） */
    public int getQueuePosition(String sessionId) {
        List<String> queue = redisTemplate.opsForList().range(QUEUE_KEY, 0, -1);
        if (queue == null) return 0;
        int pos = 0;
        for (String sid : queue) {
            if (getStatus(sid) == SessionStatus.WAITING_OPERATOR) {
                pos++;
                if (sid.equals(sessionId)) return pos;
            }
        }
        return 0;
    }

    /** 获取排队列表 */
    public List<String> getWaitingQueue() {
        List<String> q = redisTemplate.opsForList().range(QUEUE_KEY, 0, -1);
        return q != null ? q : List.of();
    }

    // ==================== 待确认转人工 ====================

    public void setPendingHandoff(String sessionId, String triggerType) {
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        hash.put(sessionKey(sessionId), FIELD_PENDING_HANDOFF, triggerType);
        touch(sessionId);
    }

    public boolean hasPendingHandoff(String sessionId) {
        String val = (String) redisTemplate.opsForHash().get(sessionKey(sessionId), FIELD_PENDING_HANDOFF);
        return val != null && !val.isBlank();
    }

    public void clearPendingHandoff(String sessionId) {
        redisTemplate.opsForHash().delete(sessionKey(sessionId), FIELD_PENDING_HANDOFF);
    }

    // ==================== 合规标记 ====================

    public void setNeedHumanReview(String sessionId, String reason) {
        redisTemplate.opsForHash().put(sessionKey(sessionId), "needHumanReview", reason != null ? reason : "true");
        touch(sessionId);
    }

    // ==================== 会话历史 ====================

    public record SessionPage(List<SessionVO> records, long total) {}
    public record SessionVO(String sessionId, String status, String intentType, String createTime) {}

    public List<ContextMessage> getMessages(String sessionId) {
        String json = (String) redisTemplate.opsForHash().get(sessionKey(sessionId), "contextWindow");
        if (json == null || json.isBlank()) return List.of();
        try {
            List<ContextMessage> all = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
            List<ContextMessage> deduped = new java.util.ArrayList<>();
            for (int i = 0; i < all.size(); i++) {
                if (i > 0 && all.get(i).getRole().equals(all.get(i-1).getRole())
                        && all.get(i).getContent().equals(all.get(i-1).getContent())) continue;
                deduped.add(all.get(i));
            }
            return deduped;
        } catch (Exception e) { return List.of(); }
    }

    public SessionPage listSessions(Long userId, int page, int size) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<ChatSession> mpPage =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .ne(ChatSession::getStatus, "CLOSED")
                .orderByDesc(ChatSession::getCreateTime);
        var result = sessionMapper.selectPage(mpPage, wrapper);
        List<SessionVO> records = result.getRecords().stream()
                .map(s -> new SessionVO(s.getSessionId(), s.getStatus(), s.getIntentType(),
                        s.getCreateTime() != null ? s.getCreateTime().toString() : ""))
                .toList();
        return new SessionPage(records, result.getTotal());
    }

    /** 设置单个字段 */
    public void setField(String sessionId, String field, String value) {
        redisTemplate.opsForHash().put(sessionKey(sessionId), field, value);
        touch(sessionId);
    }

    public void touch(String sessionId) {
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        hash.put(sessionKey(sessionId), FIELD_LAST_ACTIVE, LocalDateTime.now().toString());
        redisTemplate.expire(sessionKey(sessionId), SESSION_TTL);
    }

    private String sessionKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }
}
