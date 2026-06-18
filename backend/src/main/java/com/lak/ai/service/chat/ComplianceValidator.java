package com.lak.ai.service.chat;

import com.lak.ai.model.bo.AgentResponse;
import com.lak.ai.model.bo.RagFragment;
import com.lak.ai.security.filter.SensitiveWordPreCheckFilter;
import com.lak.ai.service.rag.tracer.SourceTracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 合规校验器 — AI 答复返回前的最后一道安全屏障。
 * <p>
 * 校验: 敏感词后置 + 溯源完整性 + 答复长度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceValidator {

    private final SensitiveWordPreCheckFilter sensitiveWordFilter;
    private final SourceTracer sourceTracer;

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

        // 1. 答复长度校验
        if (answer.length() < 5) {
            log.warn("合规校验失败: 答复过短, answer={}", answer);
            return false;
        }

        // 2. 敏感词后置校验 — 复用 SensitiveWordPreCheckFilter 的词库
        if (sensitiveWordFilter.getWordCount() > 0 && sensitiveWordFilter.containsSensitiveWord(answer)) {
            log.warn("合规校验失败: AI答复命中敏感词");
            return false;
        }

        // 3. 溯源完整性校验 — 非兜底答复必须有溯源
        if (fragments != null && !fragments.isEmpty() && !"FALLBACK".equals(response.getIntentType())) {
            if (!sourceTracer.validateHasSources(fragments)) {
                log.warn("合规校验失败: 缺少溯源引用");
                return false;
            }
        }

        return true;
    }

}
