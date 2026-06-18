package com.lak.ai.service.agent.sub;

import com.lak.ai.constant.RagConstants;
import com.lak.ai.model.bo.RagFragment;
import com.lak.ai.service.rag.retriever.HybridRetriever;
import com.lak.ai.service.rag.tracer.SourceTracer;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * PolicyAgent 工具类 — 暴露 HybridRetriever 和 SourceTracer 为 LLM 可调用工具。
 */
@Component
@RequiredArgsConstructor
public class PolicyAgentTools {

    private final HybridRetriever retriever;
    private final SourceTracer tracer;

    @Tool("检索政法政策法规库，返回与查询相关的法规条文。每次回答问题前应该先调用这个工具获取最新政策依据。")
    public String searchPolicyDocs(
            @P("要检索的政策法规问题，使用完整的问题描述") String query) {
        List<RagFragment> fragments = retriever.search(query, RagConstants.COLLECTION_POLICY);
        if (fragments.isEmpty()) {
            return "未检索到相关政策法规资料。";
        }
        List<SourceTracer.SourceCitation> sources = tracer.buildCitations(fragments);
        return sources.stream()
                .map(s -> String.format("【%s 第%s条】（生效: %s）\n%s",
                        s.sourceNo() != null ? s.sourceNo() : s.docTitle(),
                        s.articleNo() != null ? s.articleNo() : "?",
                        s.effectiveDate() != null ? s.effectiveDate() : "未知",
                        s.fragment()))
                .collect(Collectors.joining("\n\n---\n\n"));
    }
}
