package com.lak.ai.service.rag.tracer;

import com.lak.ai.model.bo.RagFragment;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 溯源追踪器 — 从检索片段中组装溯源引用。
 * <p>
 * 每条 AI 答复必须携带文档来源（docId）+ 文件编号（sourceNo）+ 生效时间（effectiveDate）。
 */
@Service
public class SourceTracer {

    /**
     * 从检索结果中提取溯源信息。
     *
     * @param fragments 混合检索返回的片段列表
     * @return 溯源引用列表（前端 SourceCitation 组件展示）
     */
    public List<SourceCitation> buildCitations(List<RagFragment> fragments) {
        return fragments.stream()
                .map(this::toCitation)
                .toList();
    }

    /**
     * 验证答复是否包含必要的溯源信息。
     *
     * @return true 如果至少有一条溯源引用
     */
    public boolean validateHasSources(List<RagFragment> fragments) {
        return fragments != null && !fragments.isEmpty()
                && fragments.stream().anyMatch(f -> f.getDocId() != null && f.getSourceNo() != null);
    }

    private SourceCitation toCitation(RagFragment f) {
        return new SourceCitation(
                f.getDocId(),
                f.getDocTitle(),
                f.getSourceNo(),
                f.getArticleNo(),
                f.getChapter(),
                f.getEffectiveDate(),
                f.getText(),     // 原文片段
                f.getScore()
        );
    }

    /**
     * 溯源引用结构 — 前端 SourceCitation 组件的数据源。
     */
    public record SourceCitation(
            String docId,
            String docTitle,
            String sourceNo,
            String articleNo,
            String chapter,
            java.time.LocalDate effectiveDate,
            String fragment,
            double score
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("docId", docId);
            map.put("title", docTitle);
            map.put("sourceNo", sourceNo);
            if (articleNo != null) map.put("articleNo", articleNo);
            if (chapter != null) map.put("chapter", chapter);
            if (effectiveDate != null) map.put("effectiveDate", effectiveDate.toString());
            map.put("fragment", fragment);
            return map;
        }
    }
}
