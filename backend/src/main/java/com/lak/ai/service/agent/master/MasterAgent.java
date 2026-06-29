package com.lak.ai.service.agent.master;

import com.lak.ai.model.bo.AgentRequest;
import com.lak.ai.model.bo.AgentResponse;
import com.lak.ai.model.bo.RoutingDecisionBO;
import com.lak.ai.service.agent.master.ConfidenceEvaluator.ConfidenceResult;
import com.lak.ai.service.agent.master.IntentClassifier.ClassificationResult;
import com.lak.ai.service.chat.context.ContextWindow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 主Agent — 意图识别 + 置信度评估 + 路由分发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MasterAgent {

    private final IntentClassifier intentClassifier;
    private final ConfidenceEvaluator confidenceEvaluator;
    private final RouteDispatcher routeDispatcher;
    private final FallbackHandler fallbackHandler;
    private final ContextWindow contextWindow;

    /**
     * 处理用户消息，返回路由决策。
     */
    public RoutingDecisionBO route(AgentRequest request) {
        long start = System.currentTimeMillis();

        // 1. 意图分类（含硬规则前置检测）
        ClassificationResult classification = intentClassifier.classify(request.getMessage());

        // 2. 置信度评估
        ConfidenceResult confidence = confidenceEvaluator.evaluate(classification, request.getMessage());

        // 3. 路由分发（传递 needsHuman）
        RoutingDecisionBO decision = routeDispatcher.dispatch(confidence, classification.costMs(),
                classification.needsHuman());

        // 4. 追加上下文
        if (request.getSessionId() != null) {
            contextWindow.append(request.getSessionId(), "user", request.getMessage());
        }

        long totalCost = System.currentTimeMillis() - start;
        log.info("主Agent处理完成, sessionId={}, intent={}, confidence={}, decision={}, cost={}ms",
                request.getSessionId(), decision.getIntentType(),
                decision.getConfidence(), decision.isNeedsHuman() ? "HUMAN" :
                        decision.isFallback() ? "FALLBACK" : decision.getTargetAgentId(),
                totalCost);

        return decision;
    }

    /**
     * 兜底处理 — 不调用大模型生成答复。
     */
    public AgentResponse fallback(String sessionId, RoutingDecisionBO decision) {
        String intent = decision.getIntentType() != null ? decision.getIntentType().name() : null;
        return fallbackHandler.handle(sessionId, decision.getConfidence(), decision.getReasoning(), intent);
    }
}
