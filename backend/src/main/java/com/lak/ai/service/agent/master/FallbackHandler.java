package com.lak.ai.service.agent.master;

import com.lak.ai.enums.SessionStatus;
import com.lak.ai.model.bo.AgentResponse;
import com.lak.ai.service.chat.HandoffSummaryGenerator;
import com.lak.ai.service.chat.session.SessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

/**
 * 兜底处理器 — 按三步递进策略：
 * <p>
 * 1 次兜底 → 温和引导，建议重述（不提转人工）<br>
 * 2 次连续兜底 → 提议转人工（A类确认）<br>
 * 用户主动请求 → 直接转人工（B类直转）
 */
@Slf4j
@Service
public class FallbackHandler {

    private final SessionManager sessionManager;
    private final HandoffSummaryGenerator summaryGenerator;
    private final com.lak.ai.service.audit.OperatorAuditLogger auditLogger;

    public FallbackHandler(SessionManager sessionManager,
                            HandoffSummaryGenerator summaryGenerator,
                            com.lak.ai.service.audit.OperatorAuditLogger auditLogger) {
        this.sessionManager = sessionManager;
        this.summaryGenerator = summaryGenerator;
        this.auditLogger = auditLogger;
    }

    // 第1次兜底 — 不提转人工，引导重述
    private static final String TEMPLATE_LOW_CONFIDENCE = """
            抱歉，我暂时没能准确理解您的问题。建议您尝试用更具体的描述重新提问，例如：
            - "办理身份证需要什么材料？"
            - "行政复议的申请时效是多久？"
            - "殴打他人怎么处罚？"
            """;

    // 闲聊模板 — 友好介绍能力
    private static final String TEMPLATE_CHITCHAT = """
            您好！我是公安智能助手，可以为您提供以下服务：

            📋 **政策法规咨询** — 治安管理、交通管理、户籍、出入境等法规查询
            📝 **办事流程指引** — 身份证、护照、户籍迁移等办事指南
            📮 **投诉建议登记** — 对公安工作的投诉、建议、举报

            请描述您的问题，我将尽力为您解答。
            """;

    // 第2次连续兜底 — 提议转人工
    private static final String TEMPLATE_HANDOFF_CONFIRM = """
            这个问题我暂时把握不准，需要帮您转接人工坐席确认吗？
            （回复"好的"确认转接，回复"不用了"继续自助咨询）

            如情况紧急，请直接拨打 12345 政务服务热线。
            """;

    // ===== A 类：第1次兜底，不提转人工 =====

    public AgentResponse handleLowConfidence(String sessionId, double confidence, String reason) {
        return AgentResponse.builder()
                .answer(TEMPLATE_LOW_CONFIDENCE)
                .sources(Collections.emptyList())
                .confidence(confidence)
                .intentType("FALLBACK")
                .build();
    }

    // ===== A 类：第2次连续兜底，提议转人工 =====

    public AgentResponse handleWithConfirm(String sessionId, double confidence, String reason,
                                            String triggerType) {
        sessionManager.setPendingHandoff(sessionId, triggerType);
        return AgentResponse.builder()
                .answer(TEMPLATE_HANDOFF_CONFIRM)
                .sources(Collections.emptyList())
                .confidence(confidence)
                .intentType("FALLBACK")
                .extra(Map.of("state", "PENDING_HANDOFF", "triggerType", triggerType))
                .build();
    }

    // ===== B 类：用户主动请求，直接转人工 =====

    public AgentResponse handleDirectHandoff(String sessionId, double confidence, String reason) {
        String triggerType = "USER_REQUEST";
        summaryGenerator.generateAndCache(sessionId, triggerType);
        int position = sessionManager.enqueueWaiting(sessionId, triggerType);
        auditLogger.log(sessionId, null, "SESSION_ENQUEUE_OPERATOR", "USER_REQUEST, position=" + position);
        log.info("用户主动请求转人工, sessionId={}, 排队位置={}", sessionId, position);
        return AgentResponse.builder()
                .answer(String.format(
                        "正在为您转接人工坐席，当前排在第 %d 位。如情况紧急，请拨打 12345 政务服务热线。",
                        position))
                .sources(Collections.emptyList())
                .confidence(confidence)
                .intentType("REQUEST_HUMAN")
                .extra(Map.of("state", "WAITING_OPERATOR", "queuePosition", position))
                .build();
    }

    // ===== 用户确认/拒绝转人工 =====

    public AgentResponse confirmHandoff(String sessionId) {
        sessionManager.clearPendingHandoff(sessionId);
        String triggerType = "USER_CONFIRMED";
        summaryGenerator.generateAndCache(sessionId, triggerType);
        int position = sessionManager.enqueueWaiting(sessionId, triggerType);
        sessionManager.transition(sessionId, SessionStatus.WAITING_OPERATOR);
        auditLogger.log(sessionId, null, "SESSION_ENQUEUE_OPERATOR", "USER_CONFIRMED, position=" + position);
        log.info("用户确认转人工, sessionId={}, 排队位置={}", sessionId, position);
        return AgentResponse.builder()
                .answer(String.format(
                        "正在为您转接人工坐席，当前排在第 %d 位。请稍候...\n如情况紧急，请拨打 12345 政务服务热线。",
                        position))
                .sources(Collections.emptyList())
                .confidence(1.0)
                .intentType("FALLBACK")
                .extra(Map.of("state", "WAITING_OPERATOR", "queuePosition", position))
                .build();
    }

    public AgentResponse rejectHandoff(String sessionId) {
        sessionManager.clearPendingHandoff(sessionId);
        auditLogger.log(sessionId, null, "SESSION_HANDOFF_REJECTED", "User rejected handoff");
        return AgentResponse.builder()
                .answer("好的，您可以继续向我提问，我将尽力为您解答。")
                .sources(Collections.emptyList())
                .confidence(0.5)
                .intentType("FALLBACK")
                .build();
    }

    // ===== 闲聊专用 =====

    public AgentResponse handleChitchat() {
        return AgentResponse.builder()
                .answer(TEMPLATE_CHITCHAT)
                .sources(Collections.emptyList())
                .confidence(0.5)
                .intentType("CHITCHAT")
                .build();
    }

    // ===== 旧接口兼容 =====

    public AgentResponse handle(String sessionId, double confidence, String reason) {
        return handleLowConfidence(sessionId, confidence, reason);
    }

    public AgentResponse handle(String sessionId, double confidence, String reason, String intentType) {
        if ("CHITCHAT".equals(intentType)) {
            return handleChitchat();
        }
        return handleLowConfidence(sessionId, confidence, reason);
    }
}
