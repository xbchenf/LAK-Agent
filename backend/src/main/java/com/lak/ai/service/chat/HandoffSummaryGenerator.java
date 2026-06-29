package com.lak.ai.service.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lak.ai.model.bo.ContextMessage;
import com.lak.ai.model.bo.HandoffSummaryBO;
import com.lak.ai.service.agent.master.HandoffSummaryService;
import com.lak.ai.service.chat.session.SessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 转人工摘要生成器 — 调用 LLM 生成结构化摘要并缓存到 Redis。
 */
@Slf4j
@Service
public class HandoffSummaryGenerator {

    private final HandoffSummaryService summaryService;
    private final SessionManager sessionManager;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public HandoffSummaryGenerator(HandoffSummaryService summaryService,
                                    SessionManager sessionManager,
                                    StringRedisTemplate redisTemplate) {
        this.summaryService = summaryService;
        this.sessionManager = sessionManager;
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 异步生成并缓存摘要。
     */
    public void generateAndCache(String sessionId, String transferReason) {
        new Thread(() -> {
            try {
                List<ContextMessage> messages = sessionManager.getMessages(sessionId);
                if (messages.isEmpty()) {
                    log.warn("会话无消息, 跳过摘要生成, sessionId={}", sessionId);
                    return;
                }

                // 格式化对话历史为文本
                String history = messages.stream()
                        .map(m -> m.getRole() + ": " + m.getContent())
                        .collect(Collectors.joining("\n"));

                // 调用 LLM 生成摘要
                HandoffSummaryBO summary = summaryService.generate(history, transferReason);

                // 缓存到 Redis（TTL 30分钟）
                String json = objectMapper.writeValueAsString(summary);
                redisTemplate.opsForHash().put(
                        "session:" + sessionId, "handoffSummary", json);
                redisTemplate.expire("session:" + sessionId, java.time.Duration.ofSeconds(1800));

                log.info("转人工摘要已生成, sessionId={}, coreQuestion={}", sessionId, summary.getCoreQuestion());
            } catch (Exception e) {
                log.warn("转人工摘要生成失败, sessionId={}, error={}", sessionId, e.getMessage());
            }
        }).start();
    }

    /**
     * 从 Redis 缓存读取摘要。
     */
    public HandoffSummaryBO getCached(String sessionId) {
        try {
            String json = (String) redisTemplate.opsForHash()
                    .get("session:" + sessionId, "handoffSummary");
            if (json == null || json.isBlank()) return null;
            return objectMapper.readValue(json, HandoffSummaryBO.class);
        } catch (Exception e) {
            return null;
        }
    }
}
