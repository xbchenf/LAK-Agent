package com.lak.ai.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

/**
 * Qdrant Collection 初始化 — 启动时确保 / VectorParams 存在。
 */
@Slf4j
@Component
public class QdrantCollectionInitializer {

    private static final int VECTOR_DIMENSION = 1024; // text-embedding-v4 实际维度

    private QdrantClient client;

    @PostConstruct
    public void init() {
        client = new QdrantClient(
                QdrantGrpcClient.newBuilder("localhost", 6334, false).build());

        // 创建 Collection（幂等操作，已存在则跳过）
        ensureCollection("lak_policy_docs", "政法政策法规");
        ensureCollection("lak_procedure_docs", "公安办事指南");
    }

    private void ensureCollection(String name, String description) {
        try {
            // 检查是否存在
            boolean exists = client.collectionExistsAsync(name).get();
            if (!exists) {
                client.createCollectionAsync(name,
                        Collections.VectorParams.newBuilder()
                                .setSize(VECTOR_DIMENSION)
                                .setDistance(Collections.Distance.Cosine)
                                .build()
                ).get();
                log.info("Qdrant Collection 创建成功: {}, dim={}, desc={}", name, VECTOR_DIMENSION, description);
            } else {
                log.info("Qdrant Collection 已存在: {}", name);
            }
        } catch (ExecutionException | InterruptedException e) {
            log.warn("Qdrant Collection 创建异常 ({}): {}", name, e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {
        if (client != null) {
            client.close();
        }
    }
}
