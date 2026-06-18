package com.lak.ai.service.chat.slot;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lak.ai.enums.SessionStatus;
import com.lak.ai.service.chat.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class SlotFillingEngineTest {

    @Mock private SessionManager sessionManager;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private HashOperations<String, Object, Object> hashOperations;

    private SlotFillingEngine engine;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String SESSION_ID = "test-session-1";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        engine = new SlotFillingEngine(sessionManager, redisTemplate, objectMapper);
    }

    @Test
    void shouldReturnFirstSlotPrompt_onStart() {
        String prompt = engine.startFilling(SESSION_ID);

        assertThat(prompt).contains("哪类问题");
        assertThat(prompt).contains("执法建议");
        verify(sessionManager).transition(SESSION_ID, SessionStatus.COLLECT_INFO);
    }

    @Test
    void shouldAdvanceToNextSlot_whenValidResponse() {
        // 模拟当前槽位为 0（complaintType）
        when(hashOperations.get(eq("session:" + SESSION_ID), eq("slotValues"))).thenReturn(null);
        when(hashOperations.get(eq("session:" + SESSION_ID), eq("currentSlot"))).thenReturn("0");
        when(hashOperations.get(eq("session:" + SESSION_ID), eq("fillRound"))).thenReturn("1");

        SlotFillingEngine.FillingResult result = engine.processResponse(SESSION_ID, "执法建议");

        assertThat(result.done()).isFalse();
        assertThat(result.nextPrompt()).contains("您的称呼");
        verify(hashOperations, atLeastOnce()).put(anyString(), eq("slotValues"), anyString());
    }

    @Test
    void shouldRetry_whenPhoneFormatInvalid() {
        // 模拟当前槽位为 2（contactPhone）
        when(hashOperations.get(eq("session:" + SESSION_ID), eq("slotValues"))).thenReturn(null);
        when(hashOperations.get(eq("session:" + SESSION_ID), eq("currentSlot"))).thenReturn("2");

        SlotFillingEngine.FillingResult result = engine.processResponse(SESSION_ID, "abc123");

        assertThat(result.needsRetry()).isTrue();
        assertThat(result.retryMessage()).contains("手机号格式不正确");
    }

    @Test
    void shouldCompleteAllRequiredSlots_whenLastSlotFilled() throws Exception {
        // 使用可变的 Map 模拟 Redis 存储
        java.util.concurrent.atomic.AtomicReference<String> storedJson = new java.util.concurrent.atomic.AtomicReference<>();
        Map<String, String> existingSlots = new java.util.HashMap<>();
        existingSlots.put("complaintType", "执法建议");
        existingSlots.put("contactName", "张三");
        existingSlots.put("contactPhone", "13800138000");
        existingSlots.put("attachment", "无");
        storedJson.set(objectMapper.writeValueAsString(existingSlots));

        // HGET — 返回当前存储的 JSON
        when(hashOperations.get(eq("session:" + SESSION_ID), eq("slotValues")))
                .thenAnswer(inv -> storedJson.get());

        // HSET — 捕获写入值（void 方法用 doAnswer）
        doAnswer(inv -> {
            storedJson.set(inv.getArgument(2, String.class));
            return null;
        }).when(hashOperations).put(eq("session:" + SESSION_ID), eq("slotValues"), anyString());

        when(hashOperations.get(eq("session:" + SESSION_ID), eq("currentSlot"))).thenReturn("3");
        when(hashOperations.get(eq("session:" + SESSION_ID), eq("fillRound"))).thenReturn("4");

        SlotFillingEngine.FillingResult result = engine.processResponse(SESSION_ID, "小区物业乱收费，希望解决");

        assertThat(result.done()).isTrue();
        assertThat(result.slotValues()).containsKeys(
                "complaintType", "contactName", "contactPhone", "description");
        verify(sessionManager).transition(SESSION_ID, SessionStatus.TICKET_SUBMIT);
    }

    @Test
    void shouldFallback_whenExceedMaxRounds() {
        when(hashOperations.get(eq("session:" + SESSION_ID), eq("slotValues"))).thenReturn(null);
        when(hashOperations.get(eq("session:" + SESSION_ID), eq("currentSlot"))).thenReturn("0");
        when(hashOperations.get(eq("session:" + SESSION_ID), eq("fillRound"))).thenReturn("5");

        SlotFillingEngine.FillingResult result = engine.processResponse(SESSION_ID, "执法建议");

        assertThat(result.done()).isTrue();
        assertThat(result.nextPrompt()).contains("超时");
        assertThat(result.nextPrompt()).contains("人工客服");
        verify(sessionManager).transition(SESSION_ID, SessionStatus.FALLBACK);
    }

    @Test
    void shouldReturnAllSlotValues_afterComplete() throws Exception {
        Map<String, String> slots = Map.of("complaintType", "服务投诉", "description", "test");
        String json = objectMapper.writeValueAsString(slots);
        when(hashOperations.get(eq("session:" + SESSION_ID), eq("slotValues"))).thenReturn(json);

        Map<String, String> result = engine.getAllSlotValues(SESSION_ID);

        assertThat(result).containsEntry("complaintType", "服务投诉");
        assertThat(result).containsEntry("description", "test");
    }
}
