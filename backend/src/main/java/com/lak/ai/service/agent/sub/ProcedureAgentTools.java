package com.lak.ai.service.agent.sub;

import com.lak.ai.constant.RagConstants;
import com.lak.ai.model.bo.RagFragment;
import com.lak.ai.service.rag.retriever.HybridRetriever;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * ProcedureAgent 检索工具 — 代码层面强制调用，不依赖 LLM 自主决策。
 */
@Component
@RequiredArgsConstructor
public class ProcedureAgentTools {

    private final HybridRetriever retriever;

    public String search(String query) {
        List<RagFragment> fragments = retriever.search(query, RagConstants.COLLECTION_PROCEDURE);
        if (fragments.isEmpty()) {
            return "未检索到相关办事指南资料。";
        }
        return fragments.stream()
                .map(f -> String.format("【%s】\n%s",
                        f.getDocTitle() != null ? f.getDocTitle() : "办事指南",
                        f.getText()))
                .collect(Collectors.joining("\n\n---\n\n"));
    }
}
