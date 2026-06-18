package com.lak.ai.service.agent.sub;

import com.lak.ai.constant.RagConstants;
import com.lak.ai.enums.IntentType;
import com.lak.ai.model.bo.RagFragment;
import com.lak.ai.service.rag.retriever.HybridRetriever;
import com.lak.ai.service.rag.tracer.SourceTracer;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 政策咨询 Agent — 政法政策库 RAG 检索 + 带原文溯源答复。
 */
@Slf4j
@Component
public class PolicyAgent extends AbstractRagAgent {

    private final String ragTemplate;

    public PolicyAgent(HybridRetriever retriever, SourceTracer tracer, ChatModel chatModel) {
        super(retriever, tracer, chatModel);
        this.ragTemplate = loadTemplate();
    }

    @Override
    public String getAgentId() {
        return "agent-policy";
    }

    @Override
    public String getAgentName() {
        return "政策咨询Agent";
    }

    @Override
    public IntentType[] getSupportedIntents() {
        return new IntentType[]{IntentType.POLICY_CONSULT};
    }

    @Override
    protected String getCollection() {
        return RagConstants.COLLECTION_POLICY;
    }

    @Override
    protected String buildRagPrompt(String userMessage, List<RagFragment> fragments) {
        String context = fragments.stream()
                .map(f -> String.format("【来源: %s 第%s条】%s",
                        f.getDocTitle() != null ? f.getDocTitle() : "未知文档",
                        f.getArticleNo() != null ? f.getArticleNo() : "?",
                        f.getText()))
                .collect(Collectors.joining("\n\n"));
        if (context.isBlank()) {
            context = "未检索到相关政策法规资料。";
        }
        return ragTemplate
                .replace("{{question}}", userMessage)
                .replace("{{context}}", context);
    }

    private String loadTemplate() {
        try {
            return new String(
                    new ClassPathResource("config/prompts/policy-rag.txt")
                            .getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            log.warn("政策RAG Prompt加载失败", e);
            return "根据以下政策法规资料回答问题。\n问题: {{question}}\n资料: {{context}}";
        }
    }
}
