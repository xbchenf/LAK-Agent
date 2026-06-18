package com.lak.ai.service.chat.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lak.ai.model.bo.ContextMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文窗口管理 — 滑动窗口（保留最近 N 轮），存储在 Redis Hash 中。
 * <p>
 * 上下文限制: 最近 10 轮 / Token 上限 6000。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextWindow {

    private static final int MAX_ROUNDS = 10;
    private static final int MAX_TOKENS = 6000;
    private static final String FIELD_CONTEXT = "contextWindow";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 追加消息到上下文窗口。
     */
    public void append(String sessionId, String role, String content) {
        List<ContextMessage> context = load(sessionId);
        context.add(ContextMessage.builder()
                .role(role)
                .content(content)
                .timestamp(System.currentTimeMillis())
                .build());
        // 滑动窗口 — 超出轮数时移除最早的消息
        int maxMessages = MAX_ROUNDS * 2; // 每轮 2 条（user + assistant）
        while (context.size() > maxMessages) {
            context.remove(0);
        }
        save(sessionId, context);
    }

    /**
     * 获取上下文消息列表（按时间正序）。
     */
    public List<ContextMessage> getContext(String sessionId) {
        return load(sessionId);
    }

    /**
     * 获取裁剪后的上下文字符串（用于送入大模型）。
     * 超出 Token 上限时从最早的消息开始丢弃。
     */
    public String buildPromptContext(String sessionId) {
        List<ContextMessage> context = load(sessionId);
        StringBuilder sb = new StringBuilder();
        int estimatedTokens = 0;
        // 从最早到最新
        for (ContextMessage msg : context) {
            int msgTokens = estimateTokens(msg.getContent());
            if (estimatedTokens + msgTokens > MAX_TOKENS) {
                break; // 超出上限，丢弃更早的消息
            }
            sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
            estimatedTokens += msgTokens;
        }
        return sb.toString();
    }

    /**
     * 清空上下文。
     */
    public void clear(String sessionId) {
        redisTemplate.opsForHash().delete("session:" + sessionId, FIELD_CONTEXT);
    }

    /**
     * 粗略 Token 估算 — 中文字符按 1 token，英文单词按 1 token。
     */
    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        // 简化: 中文字符 ≈ 1 token, 英文 ≈ 字符数/4
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        return chineseChars + (otherChars / 4);
    }

    private List<ContextMessage> load(String sessionId) {
        String json = (String) redisTemplate.opsForHash().get("session:" + sessionId, FIELD_CONTEXT);
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("上下文反序列化失败, sessionId={}", sessionId);
            return new ArrayList<>();
        }
    }

    private void save(String sessionId, List<ContextMessage> context) {
        try {
            String json = objectMapper.writeValueAsString(context);
            redisTemplate.opsForHash().put("session:" + sessionId, FIELD_CONTEXT, json);
        } catch (JsonProcessingException e) {
            log.error("上下文序列化失败, sessionId={}", sessionId, e);
        }
    }
}
