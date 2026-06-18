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
 * 办事指引 Agent — 办事指南库 RAG 检索 + 带原文溯源答复。
 */
@Slf4j
@Component
public class ProcedureAgent extends AbstractRagAgent {

    private final String ragTemplate;

    public ProcedureAgent(HybridRetriever retriever, SourceTracer tracer, ChatModel chatModel) {
        super(retriever, tracer, chatModel);
        this.ragTemplate = loadTemplate();
    }

    @Override
    public String getAgentId() {
        return "agent-procedure";
    }

    @Override
    public String getAgentName() {
        return "办事指引Agent";
    }

    @Override
    public IntentType[] getSupportedIntents() {
        return new IntentType[]{IntentType.PROCEDURE_GUIDE};
    }

    @Override
    protected String getCollection() {
        return RagConstants.COLLECTION_PROCEDURE;
    }

    @Override
    protected String buildRagPrompt(String userMessage, List<RagFragment> fragments) {
        String context = fragments.stream()
                .map(f -> String.format("【%s】%s",
                        f.getDocTitle() != null ? f.getDocTitle() : "办事指南",
                        f.getText()))
                .collect(Collectors.joining("\n\n"));
        if (context.isBlank()) {
            context = "未检索到相关办事指南资料。";
        }
        return ragTemplate
                .replace("{{question}}", userMessage)
                .replace("{{context}}", context);
    }

    private String loadTemplate() {
        try {
            return new String(
                    new ClassPathResource("config/prompts/procedure-rag.txt")
                            .getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );
        } catch (IOException e) {
            log.warn("办事RAG Prompt加载失败", e);
            return "根据以下办事指南回答问题。\n问题: {{question}}\n资料: {{context}}";
        }
    }
}
