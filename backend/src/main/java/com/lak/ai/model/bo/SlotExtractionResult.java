package com.lak.ai.model.bo;

import lombok.Builder;

/**
 * LLM 槽位提取结果 — 结构化 JSON 对应的 BO。
 * <p>
 * LLM 输出经 JSON Schema 校验后映射为此对象。
 */
@Builder
public record SlotExtractionResult(
        /** fill | modify | chitchat | new_intent | cancel */
        String action,

        /** 当前操作的槽位名（如 contactPhone） */
        String targetSlot,

        /** 提取/修改后的值（fill/modify 时非空） */
        String extractedValue,

        /** LLM 自评置信度 0.0-1.0 */
        double confidence,

        /** 简短判断理由 */
        String reasoning
) {

    /** 短别名，保持可读性 */
    public boolean isFill()       { return "fill".equals(action); }
    public boolean isModify()     { return "modify".equals(action); }
    public boolean isChitchat()   { return "chitchat".equals(action); }
    public boolean isNewIntent()  { return "new_intent".equals(action); }
    public boolean isCancel()     { return "cancel".equals(action); }

    /** 无参工厂方法 — 用于 LLM 输出解析失败时的兜底 */
    public static SlotExtractionResult fallback(String action) {
        return new SlotExtractionResult(action != null ? action : "fill", null, null, 0.5, "fallback");
    }
}
