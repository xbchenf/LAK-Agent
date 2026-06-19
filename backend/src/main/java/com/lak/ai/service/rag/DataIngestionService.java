package com.lak.ai.service.rag;

import com.lak.ai.model.bo.ChunkResult;
import com.lak.ai.service.rag.chunker.DocumentChunker;
import com.lak.ai.service.rag.embedding.EmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DataIngestionService {

    private final DocumentChunker chunker;
    private final EmbeddingService embeddingService;
    private final QdrantEmbeddingStore policyStore;
    private final QdrantEmbeddingStore procedureStore;

    public DataIngestionService(DocumentChunker chunker, EmbeddingService embeddingService,
            @org.springframework.beans.factory.annotation.Qualifier("policyEmbeddingStore") QdrantEmbeddingStore policyStore,
            @org.springframework.beans.factory.annotation.Qualifier("procedureEmbeddingStore") QdrantEmbeddingStore procedureStore) {
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.policyStore = policyStore;
        this.procedureStore = procedureStore;
    }

    public int ingest(java.io.InputStream inputStream, String collection) throws IOException {
        String fullText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        Meta meta = parseMetadata(fullText);
        String mainContent = extractMainContent(fullText);

        List<ChunkResult> chunks = chunker.chunk(
                mainContent, meta.docId(), meta.title(), "POLICY", meta.sourceNo(), meta.effectiveDate());

        QdrantEmbeddingStore store = "lak_procedure_docs".equals(collection)
                ? procedureStore : policyStore;

        for (ChunkResult chunk : chunks) {
            Map<String, Object> payload = buildPayload(chunk);
            TextSegment segment = TextSegment.from(chunk.getText(),
                    dev.langchain4j.data.document.Metadata.from(new HashMap<>(transformPayload(payload))));
            Embedding embedding = embeddingService.embedForStore(chunk.getText());
            store.add(embedding, segment);
        }

        log.info("文档导入完成, title={}, chunks={}", meta.title(), chunks.size());
        return chunks.size();
    }

    private Meta parseMetadata(String text) {
        String title = extractLine(text, 0, "").trim();
        String sourceNo = extractLine(text, 1, "").replace("发文字号: ", "").trim();
        String publishDept = extractLine(text, 2, "").replace("发布机构: ", "").trim();
        String effectiveDate = extractLine(text, 4, "").replace("生效日期: ", "").trim();
        return new Meta(title, sourceNo, effectiveDate);
    }

    private String extractMainContent(String text) {
        int idx = text.indexOf("--------------------------------------------------------------------");
        return idx > 0 ? text.substring(idx).trim() : text;
    }

    private String extractLine(String text, int lineNum, String fallback) {
        String[] lines = text.split("\n");
        return lineNum < lines.length ? lines[lineNum] : fallback;
    }

    private Map<String, Object> buildPayload(ChunkResult chunk) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("doc_id", chunk.getDocId() != null ? chunk.getDocId() : "");
        payload.put("doc_title", chunk.getDocTitle() != null ? chunk.getDocTitle() : "");
        payload.put("source_no", chunk.getSourceNo() != null ? chunk.getSourceNo() : "");
        payload.put("article_no", chunk.getArticleNo() != null ? chunk.getArticleNo() : "");
        payload.put("chapter", chunk.getChapter() != null ? chunk.getChapter() : "");
        payload.put("effective_date", chunk.getEffectiveDate() != null ? chunk.getEffectiveDate() : "");
        payload.put("chunk_index", String.valueOf(chunk.getIndex()));
        payload.put("prev_chunk_id", chunk.getPrevChunkId() != null ? chunk.getPrevChunkId() : "");
        payload.put("next_chunk_id", chunk.getNextChunkId() != null ? chunk.getNextChunkId() : "");
        return payload;
    }

    private Map<String, String> transformPayload(Map<String, Object> payload) {
        Map<String, String> result = new HashMap<>();
        payload.forEach((k, v) -> result.put(k, v != null ? v.toString() : ""));
        return result;
    }

    private record Meta(String title, String sourceNo, String effectiveDate) {
        String docId() {
            return sourceNo != null ? sourceNo.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fff]", "")
                    .replace("发", "").replace("文字号", "") : "unknown";
        }
    }
}
