package com.lak.ai.service.agent.sub;

import com.lak.ai.model.bo.AgentRequest;
import com.lak.ai.model.bo.AgentResponse;
import com.lak.ai.model.bo.RagFragment;
import com.lak.ai.service.agent.SubAgent;
import com.lak.ai.service.rag.retriever.HybridRetriever;
import com.lak.ai.service.rag.tracer.SourceTracer;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

/**
 * RAG Agent 抽象基类 — 政策咨询/办事指引 Agent 共享的检索→生成→溯源逻辑。
 */
@Slf4j
public abstract class AbstractRagAgent implements SubAgent {

    private final HybridRetriever retriever;
    private final SourceTracer tracer;
    private final ChatModel chatModel;

    protected AbstractRagAgent(HybridRetriever retriever, SourceTracer tracer, ChatModel chatModel) {
        this.retriever = retriever;
        this.tracer = tracer;
        this.chatModel = chatModel;
    }

    /** 子类提供 Qdrant Collection 名称 */
    protected abstract String getCollection();

    /** 子类提供 RAG 生成 Prompt */
    protected abstract String buildRagPrompt(String userMessage, List<RagFragment> fragments);

    @Override
    public AgentResponse process(AgentRequest request) {
        try {
            // 1. 混合检索
            List<RagFragment> fragments = retriever.search(request.getMessage(), getCollection());
            log.debug("{}, 检索结果 count={}", getAgentName(), fragments.size());

            // 2. 溯源组装
            List<SourceTracer.SourceCitation> sources = tracer.buildCitations(fragments);

            // 3. RAG 生成
            String prompt = buildRagPrompt(request.getMessage(), fragments);
            String answer = chatModel.chat(prompt);

            // 4. 构建响应
            return AgentResponse.builder()
                    .answer(answer)
                    .sources(sources.stream().map(SourceTracer.SourceCitation::toMap).toList())
                    .confidence(fragments.isEmpty() ? 0.3 : fragments.get(0).getScore())
                    .intentType(getSupportedIntents()[0].name())
                    .build();
        } catch (Exception e) {
            log.error("{} 处理失败", getAgentName(), e);
            return AgentResponse.builder()
                    .answer("系统繁忙，请稍后重试。")
                    .sources(Collections.emptyList())
                    .confidence(0.0)
                    .intentType(getSupportedIntents()[0].name())
                    .build();
        }
    }
}
