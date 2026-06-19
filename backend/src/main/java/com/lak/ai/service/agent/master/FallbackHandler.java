package com.lak.ai.service.agent.master;

import com.lak.ai.model.bo.AgentResponse;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 兜底处理器 — 低置信度/无法识别/闲聊时返回引导。
 */
@Service
public class FallbackHandler {

    private static final String LOW_CONFIDENCE = """
            抱歉，我暂时无法准确理解您的问题。为确保给您提供准确的信息，建议您：
            - 尝试用更具体的描述重新提问（如"办理身份证需要什么材料"）
            - 或通过以下方式联系公安机关：
              报警电话：110（紧急情况）
              辖区派出所：请前往您所在地派出所咨询
              工作时间：工作日 9:00-12:00, 14:00-17:00
            """;

    private static final String CHITCHAT = """
            您好！我是公安智能助手，专注于为您提供公安领域的政策法规咨询和办事流程指引服务。

            您可以向我提问例如：
            - "殴打他人怎么处罚？"
            - "办理身份证需要什么材料？"
            - "居住证到期了怎么办？"
            - "旅馆不登记旅客信息怎么处罚？"

            如需投诉或建议，请点击左侧菜单「投诉建议」提交工单。
            """;

    public AgentResponse handle(String sessionId, double confidence, String reason) {
        return build(sessionId, confidence, LOW_CONFIDENCE);
    }

    public AgentResponse handle(String sessionId, double confidence, String reason, String intentType) {
        String template = "CHITCHAT".equals(intentType) ? CHITCHAT : LOW_CONFIDENCE;
        return build(sessionId, confidence, template);
    }

    private AgentResponse build(String sessionId, double confidence, String template) {
        return AgentResponse.builder()
                .answer(template).sources(Collections.emptyList())
                .confidence(confidence).intentType("FALLBACK").build();
    }
}
