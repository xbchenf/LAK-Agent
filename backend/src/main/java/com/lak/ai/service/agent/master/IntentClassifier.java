package com.lak.ai.service.agent.master;

import com.lak.ai.enums.IntentType;
import com.lak.ai.model.bo.IntentClassification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 意图分类器 — 委托给 LangChain4j @AiService 实现。
 * <p>
 * 框架自动：@SystemMessage Prompt → LLM 调用 → JSON → POJO 映射。
 * 删除了原来 60 行手写代码（Prompt 加载、字符串替换、JSON 解析、try-catch）。
 */
@Slf4j
@Service
public class IntentClassifier {

    private final IntentService intentService;

    public IntentClassifier(IntentService intentService) {
        this.intentService = intentService;
    }

    public ClassificationResult classify(String userMessage) {
        long start = System.currentTimeMillis();
        try {
            IntentClassification result = intentService.classify(userMessage);
            String intentStr = result.getIntent();
            IntentType intentType;
            try {
                intentType = IntentType.valueOf(intentStr);
            } catch (IllegalArgumentException e) {
                intentType = IntentType.UNKNOWN;
            }
            long costMs = System.currentTimeMillis() - start;
            log.debug("意图分类完成, intent={}, confidence={}, cost={}ms", intentType, result.getConfidence(), costMs);
            return new ClassificationResult(intentType, result.getConfidence(), result.getReasoning(),
                    intentStr + ":" + result.getReasoning(), costMs);
        } catch (Exception e) {
            log.error("意图分类失败", e);
            long costMs = System.currentTimeMillis() - start;
            return new ClassificationResult(IntentType.UNKNOWN, 0.0, "分类异常: " + e.getMessage(), null, costMs);
        }
    }

    public record ClassificationResult(IntentType intentType, double modelConfidence,
                                        String reasoning, String rawResponse, long costMs) {}
}
