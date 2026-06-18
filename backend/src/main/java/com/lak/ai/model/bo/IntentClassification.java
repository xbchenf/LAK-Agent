package com.lak.ai.model.bo;

import dev.langchain4j.model.output.structured.Description;

/**
 * 意图分类结构化输出 — LangChain4j @AiService 自动提取。
 * <p>
 * 框架会将 LLM 的 JSON 输出自动反序列化为这个 POJO。
 */
public class IntentClassification {

    @Description("意图类别: POLICY_CONSULT / PROCEDURE_GUIDE / COMPLAINT_SUGGEST / CHITCHAT / UNKNOWN")
    private String intent;

    @Description("置信度 0.0-1.0, 0.9+: 非常明确有具体名称, 0.7-0.9: 意图清晰, 0.5-0.7: 有歧义, 0.3-0.5: 信息不足, <0.3: 无法判断")
    private double confidence;

    @Description("分类依据，简短说明为什么做出此判断")
    private String reasoning;

    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    @Override
    public String toString() {
        return "IntentClassification{intent=" + intent + ", confidence=" + confidence + ", reasoning=" + reasoning + "}";
    }
}
