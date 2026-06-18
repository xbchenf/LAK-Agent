package com.lak.ai.service.agent.master;

import com.lak.ai.model.bo.AgentResponse;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 兜底处理器 — 低置信度/无法识别时返回人工客服引导。
 * <p>
 * 不调用大模型。禁止 AI 自动作答。
 */
@Service
public class FallbackHandler {

    private static final String FALLBACK_TEMPLATE = """
            感谢您的咨询。为确保给您提供准确的信息，建议您通过以下方式联系我们的人工客服：
            - 服务热线：XXX-XXXXXXXX
            - 工作时间：工作日 9:00-17:00
            - 在线留言：请在官网留言板留下您的问题和联系方式
            """;

    public AgentResponse handle(String sessionId, double confidence, String reason) {
        return AgentResponse.builder()
                .answer(FALLBACK_TEMPLATE)
                .sources(Collections.emptyList())
                .confidence(confidence)
                .intentType("FALLBACK")
                .build();
    }
}
