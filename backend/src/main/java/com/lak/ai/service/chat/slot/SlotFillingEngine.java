package com.lak.ai.service.chat.slot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lak.ai.enums.SessionStatus;
import com.lak.ai.model.bo.SlotDefinition;
import com.lak.ai.service.chat.session.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Slot-Filling 引擎 — 多轮对话信息采集。
 * <p>
 * 用于投诉建议场景（子AgentC），最大 5 轮填充，5 个槽位。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SlotFillingEngine {

    private static final int MAX_FILL_ROUNDS = 5;
    private static final String FIELD_SLOTS = "slotValues";
    private static final String FIELD_CURRENT_SLOT = "currentSlot";
    private static final String FIELD_FILL_ROUND = "fillRound";

    private final SessionManager sessionManager;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final List<SlotDefinition> slotDefinitions = buildSlotDefinitions();

    private List<SlotDefinition> buildSlotDefinitions() {
        return List.of(
                SlotDefinition.builder()
                        .name("complaintType").label("投诉类型").required(true)
                        .promptTemplate("请问您要反馈的是哪类问题？\n1.治安投诉（打架斗殴、噪音扰民、赌博等）\n2.窗口服务投诉（户籍、身份证办理等）\n3.派出所/民警投诉\n4.其他")
                        .build(),
                SlotDefinition.builder()
                        .name("contactName").label("联系人").required(true)
                        .promptTemplate("请留下您的称呼：")
                        .build(),
                SlotDefinition.builder()
                        .name("contactPhone").label("联系电话").required(true)
                        .promptTemplate("请留下您的联系电话：")
                        .validationPattern("^1[3-9]\\d{9}$")
                        .validationMessage("手机号格式不正确，请输入11位手机号")
                        .build(),
                SlotDefinition.builder()
                        .name("description").label("问题描述").required(true)
                        .promptTemplate("请详细描述您的问题：")
                        .build(),
                SlotDefinition.builder()
                        .name("attachment").label("附件").required(false)
                        .promptTemplate("如有相关材料可上传（可选），没有请回复 无 ：")
                        .build()
        );
    }

    /**
     * 初始化 Slot-Filling 流程。
     *
     * @return 首个槽位的追问话术
     */
    public String startFilling(String sessionId) {
        clearSlots(sessionId);
        setCurrentSlot(sessionId, 0);
        setFillRound(sessionId, 1);
        sessionManager.transition(sessionId, SessionStatus.COLLECT_INFO);
        SlotDefinition firstSlot = slotDefinitions.get(0);
        log.debug("Slot-Filling 开始, sessionId={}, firstSlot={}", sessionId, firstSlot.getName());
        return firstSlot.getPromptTemplate();
    }

    /**
     * 处理用户的填充回复。
     *
     * @return 下一个追问话术；若全部完成则返回 null
     */
    public FillingResult processResponse(String sessionId, String userMessage) {
        int currentIndex = getCurrentSlot(sessionId);
        if (currentIndex < 0 || currentIndex >= slotDefinitions.size()) {
            return FillingResult.allDone(getAllSlotValues(sessionId));
        }

        SlotDefinition slot = slotDefinitions.get(currentIndex);

        // 校验当前槽位
        if (slot.getValidationPattern() != null && !userMessage.matches(slot.getValidationPattern())) {
            return FillingResult.retry(slot.getName(), slot.getValidationMessage());
        }

        // 存储槽位值
        setSlotValue(sessionId, slot.getName(), userMessage);

        // 检查是否已完成所有必填槽位
        int nextIndex = currentIndex + 1;
        while (nextIndex < slotDefinitions.size() && !slotDefinitions.get(nextIndex).isRequired()) {
            nextIndex++;
        }

        if (nextIndex >= slotDefinitions.size()) {
            // 所有必填完成，尝试填充可选槽位
            int optionalIndex = findFirstUnfilledOptional(sessionId);
            if (optionalIndex >= 0) {
                setCurrentSlot(sessionId, optionalIndex);
                return FillingResult.nextSlot(slotDefinitions.get(optionalIndex).getPromptTemplate());
            }
            Map<String, String> allValues = getAllSlotValues(sessionId);
            sessionManager.transition(sessionId, SessionStatus.TICKET_SUBMIT);
            log.info("Slot-Filling 完成, sessionId={}, values={}", sessionId, allValues);
            return FillingResult.allDone(allValues);
        }

        setCurrentSlot(sessionId, nextIndex);
        int round = getFillRound(sessionId) + 1;
        if (round > MAX_FILL_ROUNDS) {
            log.warn("Slot-Filling 超过最大轮数, sessionId={}, round={}", sessionId, round);
            sessionManager.transition(sessionId, SessionStatus.FALLBACK);
            return FillingResult.timeout(MAX_FILL_ROUNDS);
        }
        setFillRound(sessionId, round);
        return FillingResult.nextSlot(slotDefinitions.get(nextIndex).getPromptTemplate());
    }

    /**
     * 获取所有已填充的槽位值。
     */
    public Map<String, String> getAllSlotValues(String sessionId) {
        String json = (String) redisTemplate.opsForHash().get("session:" + sessionId, FIELD_SLOTS);
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }

    private void clearSlots(String sessionId) {
        redisTemplate.opsForHash().delete("session:" + sessionId, FIELD_SLOTS, FIELD_CURRENT_SLOT, FIELD_FILL_ROUND);
    }

    private void setSlotValue(String sessionId, String slotName, String value) {
        Map<String, String> slots = new HashMap<>(getAllSlotValues(sessionId));
        slots.put(slotName, value);
        try {
            redisTemplate.opsForHash().put("session:" + sessionId, FIELD_SLOTS, objectMapper.writeValueAsString(slots));
        } catch (JsonProcessingException e) {
            log.error("槽位值序列化失败", e);
        }
    }

    private int getCurrentSlot(String sessionId) {
        String val = (String) redisTemplate.opsForHash().get("session:" + sessionId, FIELD_CURRENT_SLOT);
        return val != null ? Integer.parseInt(val) : -1;
    }

    private void setCurrentSlot(String sessionId, int index) {
        redisTemplate.opsForHash().put("session:" + sessionId, FIELD_CURRENT_SLOT, String.valueOf(index));
    }

    private int getFillRound(String sessionId) {
        String val = (String) redisTemplate.opsForHash().get("session:" + sessionId, FIELD_FILL_ROUND);
        return val != null ? Integer.parseInt(val) : 0;
    }

    private void setFillRound(String sessionId, int round) {
        redisTemplate.opsForHash().put("session:" + sessionId, FIELD_FILL_ROUND, String.valueOf(round));
    }

    private int findFirstUnfilledOptional(String sessionId) {
        Map<String, String> slots = getAllSlotValues(sessionId);
        for (int i = 0; i < slotDefinitions.size(); i++) {
            SlotDefinition def = slotDefinitions.get(i);
            if (!def.isRequired() && !slots.containsKey(def.getName())) {
                return i;
            }
        }
        return -1;
    }

    // ===== Inner types =====

    public record FillingResult(boolean done, String nextPrompt, String retrySlot, String retryMessage,
                                 Map<String, String> slotValues) {

        public static FillingResult nextSlot(String prompt) {
            return new FillingResult(false, prompt, null, null, null);
        }

        public static FillingResult retry(String slotName, String message) {
            return new FillingResult(false, null, slotName, message, null);
        }

        public static FillingResult allDone(Map<String, String> values) {
            return new FillingResult(true, null, null, null, values);
        }

        public static FillingResult timeout(int maxRounds) {
            return new FillingResult(true, "信息采集超时（已超过" + maxRounds + "轮），" +
                    "您可以通过人工客服提交", null, null, null);
        }

        /** 是否需要重试当前槽位 */
        public boolean needsRetry() { return retrySlot != null; }
    }
}
