package com.lak.ai.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * QdrantClient Bean 配置 — 将 QdrantClient 暴露为 Spring Bean，
 * 供 QdrantCollectionInitializer、TestDataLoader 等组件注入使用。
 */
@Configuration
public class QdrantClientConfig {

    @Bean(destroyMethod = "close")
    public QdrantClient qdrantClient() {
        return new QdrantClient(
                QdrantGrpcClient.newBuilder("localhost", 6334, false).build());
    }
}
