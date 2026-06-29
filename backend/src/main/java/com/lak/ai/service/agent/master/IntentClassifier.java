package com.lak.ai.service.agent.master;

import com.lak.ai.enums.IntentType;
import com.lak.ai.model.bo.IntentClassification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 意图分类器 — 硬规则前置 + LLM 委托。
 */
@Slf4j
@Service
public class IntentClassifier {

    private final IntentService intentService;

    /** 转人工请求关键词 */
    private static final Set<String> HUMAN_REQUEST_KEYWORDS = Set.of(
            "转人工", "人工客服", "找人工", "我要找真人", "找真人",
            "有人吗", "人工坐席", "转接人工", "我要人工", "人工服务"
    );

    public IntentClassifier(IntentService intentService) {
        this.intentService = intentService;
    }

    public ClassificationResult classify(String userMessage) {
        // 1. 硬规则前置：检测人工请求（不走 LLM）
        if (isHumanRequest(userMessage)) {
            log.debug("硬规则命中: 用户请求转人工, message={}", userMessage);
            return new ClassificationResult(IntentType.REQUEST_HUMAN, 1.0,
                    "用户明确请求转人工", "HARD_RULE:REQUEST_HUMAN", 0, true);
        }

        // 2. LLM 意图分类
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
                    intentStr + ":" + result.getReasoning(), costMs, false);
        } catch (Exception e) {
            log.error("意图分类失败", e);
            long costMs = System.currentTimeMillis() - start;
            return new ClassificationResult(IntentType.UNKNOWN, 0.0, "分类异常: " + e.getMessage(), null, costMs, false);
        }
    }

    private boolean isHumanRequest(String message) {
        if (message == null || message.isBlank()) return false;
        String msg = message.trim();
        return HUMAN_REQUEST_KEYWORDS.stream().anyMatch(msg::contains);
    }

    public record ClassificationResult(IntentType intentType, double modelConfidence,
                                        String reasoning, String rawResponse, long costMs,
                                        boolean needsHuman) {}
}
