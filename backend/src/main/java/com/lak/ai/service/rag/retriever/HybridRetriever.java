package com.lak.ai.service.rag.retriever;

import com.lak.ai.model.bo.RagFragment;
import com.lak.ai.service.rag.embedding.EmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 混合检索器 —— Dense + BM25 双路检索 → RRF 融合。
 */
@Slf4j
@Service
public class HybridRetriever {

    private static final int DENSE_TOP_K = 10;
    private static final int BM25_TOP_K = 10;
    private static final int FUSION_TOP_K = 5;
    private static final double SIMILARITY_THRESHOLD = 0.55;
    private static final double DENSE_MIN_SCORE = 0.55;
    private static final int RRF_K = 60;

    private final EmbeddingService embeddingService;
    private final Bm25Index bm25Index;
    private final Map<String, QdrantEmbeddingStore> stores;

    public HybridRetriever(EmbeddingService embeddingService, Bm25Index bm25Index,
            @org.springframework.beans.factory.annotation.Qualifier("policyEmbeddingStore") QdrantEmbeddingStore policyStore,
            @org.springframework.beans.factory.annotation.Qualifier("procedureEmbeddingStore") QdrantEmbeddingStore procedureStore) {
        this.embeddingService = embeddingService;
        this.bm25Index = bm25Index;
        this.stores = Map.of(
                "lak_policy_docs", policyStore,
                "lak_procedure_docs", procedureStore);
    }

    /**
     * 混合检索: Dense + BM25 → RRF 融合。
     */
    public List<RagFragment> search(String query, String collection) {
        QdrantEmbeddingStore store = stores.get(collection);
        if (store == null) return List.of();

        try {
            var denseFuture = CompletableFuture.<List<RagFragment>>supplyAsync(() -> denseSearch(query, store));
            var bm25Future = CompletableFuture.<List<RagFragment>>supplyAsync(() -> bm25Index.search(query, BM25_TOP_K));

            List<RagFragment> denseResults = denseFuture.get(3, java.util.concurrent.TimeUnit.SECONDS);
            List<RagFragment> bm25Results = bm25Future.get(1, java.util.concurrent.TimeUnit.SECONDS);

            List<RagFragment> fused = rrfFuse(denseResults, bm25Results);

            return fused.stream()
                    .limit(FUSION_TOP_K)
                    .toList();
        } catch (Exception e) {
            log.error("混合检索失败, collection={}, query={}", collection, query, e);
            return List.of();
        }
    }

    private List<RagFragment> denseSearch(String query, QdrantEmbeddingStore store) {
        float[] queryVector = embeddingService.embed(query);
        List<Float> floatList = new ArrayList<>(queryVector.length);
        for (float v : queryVector) floatList.add(v);
        Embedding queryEmbedding = Embedding.from(floatList);
        return store.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(DENSE_TOP_K)
                        .minScore(DENSE_MIN_SCORE)
                        .build())
                .matches().stream()
                .map(this::toFragment)
                .collect(Collectors.toList());
    }

    private List<RagFragment> rrfFuse(List<RagFragment> dense, List<RagFragment> bm25) {
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        Map<String, RagFragment> bestFrag = new LinkedHashMap<>();

        for (int i = 0; i < dense.size(); i++) {
            RagFragment f = dense.get(i);
            String key = (f.getDocId() != null ? f.getDocId() : "") + ":" + (f.getChunkIndex() != null ? f.getChunkIndex() : 0);
            rrfScores.merge(key, 1.0 / (RRF_K + i + 1), Double::sum);
            bestFrag.putIfAbsent(key, f);
        }
        for (int i = 0; i < bm25.size(); i++) {
            RagFragment f = bm25.get(i);
            String key = (f.getDocId() != null ? f.getDocId() : "") + ":" + (f.getChunkIndex() != null ? f.getChunkIndex() : 0);
            rrfScores.merge(key, 1.0 / (RRF_K + i + 1), Double::sum);
            bestFrag.putIfAbsent(key, f);
        }

        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> {
                    RagFragment f = bestFrag.get(e.getKey());
                    if (f != null) f.setScore(e.getValue());
                    return f;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private RagFragment toFragment(EmbeddingMatch<TextSegment> match) {
        TextSegment segment = match.embedded();
        Map<String, Object> metadataMap = segment.metadata() != null
                ? new HashMap<>(segment.metadata().toMap()) : Collections.emptyMap();
        Map<String, String> metadata = new HashMap<>();
        metadataMap.forEach((k, v) -> metadata.put(k, v != null ? v.toString() : ""));

        return RagFragment.builder()
                .text(segment.text()).score(match.score())
                .docId(metadata.get("docId")).docTitle(metadata.get("docTitle"))
                .sourceNo(metadata.get("sourceNo")).articleNo(metadata.get("articleNo"))
                .chapter(metadata.get("chapter"))
                .effectiveDate(parseDate(metadata.get("effectiveDate")))
                .chunkIndex(parseInt(metadata.get("chunkIndex")))
                .rawPayload(new HashMap<>(metadata)).build();
    }

    private LocalDate parseDate(String d) { try { return d==null||d.isBlank()?null:LocalDate.parse(d,DateTimeFormatter.ISO_LOCAL_DATE); } catch(Exception e) { return null; } }
    private Integer parseInt(String v) { try { return v==null||v.isBlank()?null:Integer.parseInt(v); } catch(NumberFormatException e) { return null; } }
}
