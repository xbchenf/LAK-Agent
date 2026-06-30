package com.lak.ai.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

/**
 * Qdrant Collection 初始化 — 启动时确保 Collection 存在。
 */
@Slf4j
@Component
public class QdrantCollectionInitializer {

    private static final int VECTOR_DIMENSION = 1024;

    private final QdrantClient client;

    public QdrantCollectionInitializer(QdrantClient client) {
        this.client = client;
    }

    @PostConstruct
    public void init() {
        ensureCollection("lak_policy_docs");
        ensureCollection("lak_procedure_docs");
    }

    private void ensureCollection(String name) {
        try {
            boolean exists = client.collectionExistsAsync(name).get();
            if (!exists) {
                client.createCollectionAsync(name,
                        Collections.VectorParams.newBuilder()
                                .setSize(VECTOR_DIMENSION)
                                .setDistance(Collections.Distance.Cosine)
                                .build()
                ).get();
                log.info("Qdrant Collection 创建成功: {}, dim={}", name, VECTOR_DIMENSION);
            }
        } catch (ExecutionException | InterruptedException e) {
            log.warn("Qdrant 初始化异常 ({}): {}", name, e.getMessage());
        }
    }

    @PreDestroy
    public void cleanup() {}
}
