package com.lak.ai.service.rag.retriever;

import com.lak.ai.model.bo.RagFragment;
import com.lak.ai.service.rag.embedding.EmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合检索器 — Dense 向量检索 + 关键词加权重排序。
 * <p>
 * 使用 LangChain4j QdrantEmbeddingStore 进行 Dense 检索，
 * 然后在 Java 层做关键词加权 + 相似度过滤。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridRetriever {

    private static final int DENSE_TOP_K = 10;
    private static final int FUSION_TOP_K = 5;
    private static final double SIMILARITY_THRESHOLD = 0.75;

    private final EmbeddingService embeddingService;
    private final QdrantEmbeddingStore embeddingStore;

    @Value("${lak.rag.retrieval-timeout-seconds:3}")
    private int timeoutSeconds;

    /**
     * 混合检索 — Dense 检索 + 关键词加权。
     */
    public List<RagFragment> search(String query, String collection) {
        try {
            // Phase 1: Dense 向量检索
            float[] queryVector = embeddingService.embed(query);
            List<Float> floatList = new ArrayList<>(queryVector.length);
            for (float v : queryVector) floatList.add(v);
            Embedding queryEmbedding = Embedding.from(floatList);
            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(
                    EmbeddingSearchRequest.builder()
                            .queryEmbedding(queryEmbedding)
                            .maxResults(DENSE_TOP_K)
                            .minScore(SIMILARITY_THRESHOLD)
                            .build()
            ).matches();

            // Phase 2: 关键词加权 + 相似度过滤
            List<RagFragment> fragments = matches.stream()
                    .map(this::toFragment)
                    .collect(Collectors.toList());

            // Phase 3: 关键词加权重排序
            List<RagFragment> reranked = keywordBoost(fragments, query);

            return reranked.stream()
                    .filter(f -> f.getScore() >= SIMILARITY_THRESHOLD)
                    .limit(FUSION_TOP_K)
                    .toList();
        } catch (Exception e) {
            log.error("混合检索失败, collection={}, query={}", collection, query, e);
            return Collections.emptyList();
        }
    }

    private List<RagFragment> keywordBoost(List<RagFragment> fragments, String query) {
        if (fragments.isEmpty()) return fragments;

        Set<String> queryWords = tokenize(query);
        for (RagFragment frag : fragments) {
            long matchedWords = queryWords.stream()
                    .filter(w -> frag.getText().contains(w))
                    .count();
            double boost = 1.0 + (matchedWords * 0.05);
            frag.setScore(Math.min(frag.getScore() * boost, 1.0));
        }
        fragments.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return fragments;
    }

    private Set<String> tokenize(String text) {
        Set<String> words = new HashSet<>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c > 127) { // CJK character
                words.add(String.valueOf(c));
            } else if (Character.isLetterOrDigit(c)) {
                int j = i;
                while (j < text.length() && Character.isLetterOrDigit(text.charAt(j))) j++;
                words.add(text.substring(i, j).toLowerCase());
                i = j - 1;
            }
        }
        return words;
    }

    private RagFragment toFragment(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        Map<String, Object> metadataMap = segment.metadata() != null
                ? new HashMap<>(segment.metadata().toMap())
                : Collections.emptyMap();
        Map<String, String> metadata = new HashMap<>();
        metadataMap.forEach((k, v) -> metadata.put(k, v != null ? v.toString() : ""));

        return RagFragment.builder()
                .text(segment.text())
                .score(match.score())
                .docId(metadata.get("doc_id"))
                .docTitle(metadata.get("doc_title"))
                .sourceNo(metadata.get("source_no"))
                .articleNo(metadata.get("article_no"))
                .chapter(metadata.get("chapter"))
                .effectiveDate(parseDate(metadata.get("effective_date")))
                .expireDate(parseDate(metadata.get("expire_date")))
                .chunkIndex(parseInt(metadata.get("chunk_index")))
                .prevChunkId(metadata.get("prev_chunk_id"))
                .nextChunkId(metadata.get("next_chunk_id"))
                .rawPayload(new HashMap<>(metadata))
                .build();
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseInt(String val) {
        if (val == null || val.isBlank()) return null;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
