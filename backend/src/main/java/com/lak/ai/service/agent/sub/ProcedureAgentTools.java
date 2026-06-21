package com.lak.ai.service.agent.sub;

import com.lak.ai.constant.RagConstants;
import com.lak.ai.model.bo.RagFragment;
import com.lak.ai.service.rag.retriever.HybridRetriever;
import com.lak.ai.service.rag.tracer.SourceTracer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ProcedureAgent 检索工具 — 代码层面强制调用，不依赖 LLM 自主决策。
 */
@Component
@RequiredArgsConstructor
public class ProcedureAgentTools {

    private final HybridRetriever retriever;
    private final SourceTracer tracer;

    /** 检索结果 — 同时包含注入LLM的格式化文本和前端展示的结构化溯源 */
    public record SearchResult(String formattedText, List<Map<String, Object>> sources) {}

    public SearchResult search(String query) {
        List<RagFragment> fragments = retriever.search(query, RagConstants.COLLECTION_PROCEDURE);
        if (fragments.isEmpty()) {
            return new SearchResult("未检索到相关办事指南资料。", List.of());
        }
        List<SourceTracer.SourceCitation> citations = tracer.buildCitations(fragments);
        String formatted = citations.stream()
                .map(s -> String.format("【%s】\n%s",
                        s.docTitle() != null ? s.docTitle() : "办事指南",
                        s.fragment()))
                .collect(Collectors.joining("\n\n---\n\n"));
        List<Map<String, Object>> sourceMaps = citations.stream()
                .map(SourceTracer.SourceCitation::toMap)
                .collect(Collectors.toList());
        return new SearchResult(formatted, sourceMaps);
    }
}
