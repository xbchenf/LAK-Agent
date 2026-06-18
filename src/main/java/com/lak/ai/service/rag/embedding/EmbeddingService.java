package com.lak.ai.service.rag.embedding;

import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文本向量化服务 — 调用百炼 text-embedding-v4。
 * <p>
 * 输出: 1536维 Dense Vector。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final QwenEmbeddingModel embeddingModel;

    /**
     * 将单个文本转换为 Dense Vector。
     */
    public float[] embed(String text) {
        Embedding embedding = embeddingModel.embed(text).content();
        return toFloatArray(embedding.vectorAsList());
    }

    /**
     * 批量文本转换为 Dense Vector。
     */
    public List<float[]> embedBatch(List<String> texts) {
        return embeddingModel.embedAll(texts).content().stream()
                .map(emb -> toFloatArray(emb.vectorAsList()))
                .toList();
    }

    /**
     * 获取向量维度。
     */
    public int dimension() {
        return 1536;
    }

    private float[] toFloatArray(List<Float> floats) {
        float[] array = new float[floats.size()];
        for (int i = 0; i < floats.size(); i++) {
            array[i] = floats.get(i);
        }
        return array;
    }
}
