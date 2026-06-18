package com.lak.ai.service.rag.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
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

    private final EmbeddingModel embeddingModel;

    /**
     * 将单个文本转换为 Dense Vector。
     */
    public float[] embed(String text) {
        Embedding embedding = embeddingModel.embed(text).content();
        return toFloatArray(embedding.vectorAsList());
    }

    /** 预留批量接口，当前 LangChain4j 1.x 版本嵌入API待适配 */

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
