package com.lak.ai.service.agent.sub;

import com.lak.ai.constant.RagConstants;
import com.lak.ai.model.bo.RagFragment;
import com.lak.ai.service.rag.retriever.HybridRetriever;
import com.lak.ai.service.rag.tracer.SourceTracer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * PolicyAgent 检索工具 — 代码层面强制调用，不依赖 LLM 自主决策。
 */
@Component
@RequiredArgsConstructor
public class PolicyAgentTools {

    private final HybridRetriever retriever;
    private final SourceTracer tracer;

    public String search(String query) {
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
