package com.lak.ai.service.agent.master;

import com.lak.ai.enums.IntentType;
import com.lak.ai.model.bo.RoutingDecisionBO;
import com.lak.ai.service.agent.master.ConfidenceEvaluator.ConfidenceResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 路由分发器 — 根据意图 + 置信度决策路由目标。
 */
@Slf4j
@Service
public class RouteDispatcher {

    /**
     * 计算路由决策。
     */
    public RoutingDecisionBO dispatch(ConfidenceResult confidenceResult, long classificationCostMs) {
        IntentType intentType = confidenceResult.intentType();
        double confidence = confidenceResult.confidence();

        if (confidenceResult.fallback()) {
            return buildDecision(intentType, confidence, null, true,
                    confidenceResult.detail(), classificationCostMs);
        }

        // 根据意图确定目标 Agent
        String targetAgentId = intentType.getTargetAgentId();
        if (targetAgentId == null || intentType.requiresFallback()) {
            log.info("路由兜底, intent={}, confidence={}, reason={}",
                    intentType, confidence, confidenceResult.detail());
            return buildDecision(intentType, confidence, null, true,
                    confidenceResult.detail(), classificationCostMs);
        }

        log.info("路由决策, intent={}, confidence={}, target={}",
                intentType, confidence, targetAgentId);
        return buildDecision(intentType, confidence, targetAgentId, false,
                confidenceResult.detail(), classificationCostMs);
    }

    private RoutingDecisionBO buildDecision(IntentType intentType, double confidence,
                                             String targetAgentId, boolean fallback,
                                             String reasoning, long costMs) {
        return RoutingDecisionBO.builder()
                .intentType(intentType)
                .confidence(confidence)
                .targetAgentId(targetAgentId)
                .fallback(fallback)
                .reasoning(reasoning)
                .costMs(costMs)
                .build();
    }
}
