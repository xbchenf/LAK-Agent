package com.lak.ai.service.agent.master;

import com.lak.ai.model.bo.HandoffSummaryBO;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 转人工摘要 @AiService — LLM 自动生成会话摘要 JSON。
 */
public interface HandoffSummaryService {

    @SystemMessage("""
            你是公安智能客服系统的会话摘要助手。当 AI 无法回答用户问题需要转人工时，
            你需要根据对话历史生成一份结构化摘要，帮助坐席快速了解情况。

            只返回 JSON，不要输出其他内容。JSON 格式：
            {
              "userProfile": "用户画像",
              "intent": "意图类型",
              "confidence": 0.5,
              "coreQuestion": "用户核心问题（一句话）",
              "retrievedDocs": "AI检索到的法规和相似度",
              "aiResponseSummary": "AI已给出答复的简要总结",
              "transferReason": "转人工原因",
              "suggestedVerification": "建议坐席核实或跟进的事项",
              "stats": "会话统计"
            }
            """)
    @UserMessage("""
            请分析以下对话并生成转人工摘要：

            对话历史：
            {{conversation}}

            转人工原因：{{reason}}
            """)
    HandoffSummaryBO generate(@V("conversation") String conversationHistory,
                               @V("reason") String reason);
}
