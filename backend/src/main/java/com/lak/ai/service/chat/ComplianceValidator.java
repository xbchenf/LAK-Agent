package com.lak.ai.service.chat;

import com.lak.ai.model.bo.AgentResponse;
import com.lak.ai.model.bo.RagFragment;
import com.lak.ai.security.filter.SensitiveWordPreCheckFilter;
import com.lak.ai.service.chat.session.SessionManager;
import com.lak.ai.service.rag.tracer.SourceTracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 合规校验器 — AI 答复返回前的最后一道安全屏障。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceValidator {

    private final SensitiveWordPreCheckFilter sensitiveWordFilter;
    private final SourceTracer sourceTracer;
    private final SessionManager sessionManager;

    /**
     * 合规校验 — 全部通过才放行。
     *
     * @return true = 通过, false = 拦截
     */
    public boolean validate(AgentResponse response, List<RagFragment> fragments) {
        String answer = response.getAnswer();
        if (answer == null || answer.isBlank()) {
            log.warn("合规校验失败: 答复为空");
            return false;
        }

        if (answer.length() < 5) {
            log.warn("合规校验失败: 答复过短, answer={}", answer);
            return false;
        }

        if (sensitiveWordFilter.getWordCount() > 0 && sensitiveWordFilter.containsSensitiveWord(answer)) {
            log.warn("合规校验失败: AI答复命中敏感词");
            return false;
        }

        if (fragments != null && !fragments.isEmpty() && !"FALLBACK".equals(response.getIntentType())) {
            if (!sourceTracer.validateHasSources(fragments)) {
                log.warn("合规校验失败: 缺少溯源引用");
                return false;
            }
        }

        return true;
    }

    /**
     * 坐席消息合规校验 — 告警但不阻止（坐席是授权人员）。
     *
     * @return true = 有风险，false = 无问题
     */
    public boolean validateOperatorMessage(String message, String sessionId) {
        if (message == null || message.isBlank()) {
            log.warn("坐席消息合规: 消息为空, sessionId={}", sessionId);
            return true;
        }

        boolean hasRisk = false;
        if (sensitiveWordFilter.getWordCount() > 0 && sensitiveWordFilter.containsSensitiveWord(message)) {
            log.warn("坐席消息合规: 消息疑似含敏感词(已告警不阻止), sessionId={}", sessionId);
            hasRisk = true;
        }

        if (hasRisk) {
            // 标记需要人工复核
            sessionManager.setNeedHumanReview(sessionId, "OPERATOR_MESSAGE_RISK");
        }
        return hasRisk;
    }

    /**
     * 标记会话需要人工复核（T4 触发 — 合规失败后台标记，不实时转人工）。
     */
    public void markNeedHumanReview(String sessionId, String reason) {
        sessionManager.setNeedHumanReview(sessionId, reason);
        log.info("会话标记需要人工复核, sessionId={}, reason={}", sessionId, reason);
    }
}
