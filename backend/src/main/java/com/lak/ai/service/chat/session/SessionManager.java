package com.lak.ai.service.chat.session;

import com.lak.ai.enums.SessionStatus;
import com.lak.ai.mapper.ChatSessionMapper;
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
 * 会话管理器 — Redis Hash + MySQL 双写，Redis 用于热数据，MySQL 用于关联查询。
 */
@Slf4j
@Service
public class SessionManager {

    private static final String SESSION_KEY_PREFIX = "session:";
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
        // MySQL 双写 — 支撑 TicketAdapter.queryUserTickets 的关联查询
        ChatSession entity = new ChatSession();
        entity.setSessionId(sessionId);
        entity.setUserId(userId);
        entity.setStatus(SessionStatus.NEW.name());
        sessionMapper.insert(entity);
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
        // 同步更新 MySQL — 确保列表查询不再显示
        var entity = new ChatSession();
        entity.setSessionId(sessionId);
        entity.setStatus(SessionStatus.CLOSED.name());
        sessionMapper.update(entity,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ChatSession>()
                        .eq(ChatSession::getSessionId, sessionId));
        log.debug("会话关闭, sessionId={}", sessionId);
    }

    /**
     * 刷新会话 TTL。
     */
    public record SessionPage(List<SessionVO> records, long total) {}
    public record SessionVO(String sessionId, String status, String intentType, String createTime) {}

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

    public void touch(String sessionId) {
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        hash.put(sessionKey(sessionId), FIELD_LAST_ACTIVE, LocalDateTime.now().toString());
        redisTemplate.expire(sessionKey(sessionId), SESSION_TTL);
    }

    private String sessionKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }
}
