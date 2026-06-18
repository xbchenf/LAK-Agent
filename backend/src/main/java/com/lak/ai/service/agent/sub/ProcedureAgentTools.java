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
 * ProcedureAgent 工具类 — 暴露办事指南检索为 LLM 可调用工具。
 */
@Component
@RequiredArgsConstructor
public class ProcedureAgentTools {

    private final HybridRetriever retriever;
    private final SourceTracer tracer;

    @Tool("检索办事指南库，返回办理条件、所需材料、办理流程等信息。回答办事流程问题前必须先调用这个工具。")
    public String searchProcedureDocs(
            @P("要检索的办事流程问题，如'办理XX证需要什么材料'") String query) {
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
