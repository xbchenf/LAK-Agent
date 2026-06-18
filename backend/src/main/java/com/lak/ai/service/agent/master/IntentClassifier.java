package com.lak.ai.service.agent.master;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lak.ai.enums.IntentType;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 意图分类器 — 调用 LLM 结构化 JSON 输出进行意图分类 + 自评置信度。
 */
@Slf4j
@Service
public class IntentClassifier {

    private final ChatModel chatModel;
    private final String promptTemplate;
    private final ObjectMapper objectMapper;

    public IntentClassifier(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.promptTemplate = loadPromptTemplate();
    }

    public ClassificationResult classify(String userMessage) {
        long start = System.currentTimeMillis();
        try {
            String prompt = promptTemplate.replace("{{userMessage}}", userMessage);
            String responseText = chatModel.chat(
                    SystemMessage.from("你是一个精确的意图分类器，严格遵守 JSON 格式输出。"),
                    UserMessage.from(prompt)
            ).aiMessage().text();

            String json = extractJson(responseText);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = objectMapper.readValue(json, Map.class);
            String intentStr = (String) result.get("intent");
            double confidence = ((Number) result.get("confidence")).doubleValue();
            String reasoning = (String) result.get("reasoning");

            IntentType intentType;
            try {
                intentType = IntentType.valueOf(intentStr);
            } catch (IllegalArgumentException e) {
                intentType = IntentType.UNKNOWN;
            }

            long costMs = System.currentTimeMillis() - start;
            log.debug("意图分类完成, intent={}, confidence={}, cost={}ms", intentType, confidence, costMs);
            return new ClassificationResult(intentType, confidence, reasoning, responseText, costMs);
        } catch (Exception e) {
            log.error("意图分类失败", e);
            long costMs = System.currentTimeMillis() - start;
            return new ClassificationResult(IntentType.UNKNOWN, 0.0, "分类异常: " + e.getMessage(), null, costMs);
        }
    }

    private String extractJson(String response) {
        if (response == null) return "{}";
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return "{}";
    }

    private String loadPromptTemplate() {
        try {
            return new String(
                    new ClassPathResource("config/prompts/intent-classifier.txt")
                            .getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            log.warn("意图分类Prompt加载失败，使用内置模板", e);
            return "请分析用户意图，返回JSON: {\"intent\":\"POLICY_CONSULT|...\",\"confidence\":0.0-1.0,\"reasoning\":\"...\"}\n用户: {{userMessage}}";
        }
    }

    public record ClassificationResult(IntentType intentType, double modelConfidence,
                                        String reasoning, String rawResponse, long costMs) {}
}
