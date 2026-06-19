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
            感谢您的咨询。为确保给您提供准确的信息，建议您通过以下方式联系公安机关：
            - 报警电话：110（紧急情况）
            - 辖区派出所：请前往您所在地派出所咨询，或拨打派出所值班电话
            - 工作时间：工作日 9:00-12:00, 14:00-17:00
            - 在线平台：登录当地公安局"互联网+政务服务平台"
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
