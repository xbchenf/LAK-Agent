package com.lak.ai.service.chat.slot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lak.ai.model.bo.SlotExtractionResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * LLM 输出 JSON Schema 校验器 — 校验不通过时触发 reprompt。
 */
@Slf4j
public class SlotExtractionValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> VALID_ACTIONS = Set.of("fill", "modify", "chitchat", "new_intent", "cancel");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    /**
     * 从 LLM 原始输出解析并校验。校验失败抛 {@link ExtractionParseException} 由调用方 reprompt。
     */
    public SlotExtractionResult parseAndValidate(String rawLlmOutput, String currentSlotName) {
        // 1. 提取 JSON 块（LLM 可能在前后附带文字）
        String json = extractJsonBlock(rawLlmOutput);
        if (json == null) {
            throw new ExtractionParseException("LLM 输出中未找到有效 JSON 块");
        }

        // 2. 解析
        JsonNode node;
        try {
            node = MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new ExtractionParseException("JSON 格式错误: " + e.getOriginalMessage());
        }

        // 3. 必填字段检查
        String action = getStringField(node, "action", true);
        if (action == null || !VALID_ACTIONS.contains(action)) {
            throw new ExtractionParseException("action 字段无效: " + action + "，合法值: " + VALID_ACTIONS);
        }

        String targetSlot = getStringField(node, "targetSlot", false);
        if (targetSlot == null && ("fill".equals(action) || "modify".equals(action))) {
            targetSlot = currentSlotName; // fill/modify 时默认当前槽位
        }

        String extractedValue = getStringField(node, "extractedValue", false);
        if (("fill".equals(action) || "modify".equals(action)) &&
            (extractedValue == null || extractedValue.isBlank())) {
            throw new ExtractionParseException("fill/modify 时 extractedValue 不能为空");
        }

        double confidence = getDoubleField(node, "confidence", 0.5);
        String reasoning = getStringField(node, "reasoning", false);

        // 4. 手机号特殊校验
        if ("contactPhone".equals(targetSlot != null ? targetSlot : currentSlotName) &&
            "fill".equals(action) && extractedValue != null) {
            if (!PHONE_PATTERN.matcher(extractedValue.trim()).matches()) {
                throw new ExtractionParseException("手机号格式不正确: " + extractedValue);
            }
        }

        return new SlotExtractionResult(action, targetSlot, extractedValue,
                Math.max(0.0, Math.min(1.0, confidence)), reasoning);
    }

    // ---- helpers ----

    /** 从 LLM 输出中提取第一个 JSON 对象块 */
    static String extractJsonBlock(String raw) {
        if (raw == null) return null;
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        return raw.substring(start, end + 1);
    }

    private String getStringField(JsonNode node, String name, boolean required) {
        JsonNode field = node.get(name);
        if (field == null || field.isNull()) {
            if (required) throw new ExtractionParseException("缺少必填字段: " + name);
            return null;
        }
        return field.asText();
    }

    private double getDoubleField(JsonNode node, String name, double defaultValue) {
        JsonNode field = node.get(name);
        if (field == null || field.isNull()) return defaultValue;
        return field.asDouble(defaultValue);
    }

    /** 校验异常 — 非业务异常，仅用于内部控制流 */
    public static class ExtractionParseException extends RuntimeException {
        public ExtractionParseException(String message) {
            super(message);
        }
    }
}
