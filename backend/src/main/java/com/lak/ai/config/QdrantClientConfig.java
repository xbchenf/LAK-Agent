package com.lak.ai.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QdrantClientConfig {

    @Value("${qdrant.host:localhost}")
    private String host;

    @Value("${qdrant.grpc-port:6334}")
    private int grpcPort;

    @Bean(destroyMethod = "close")
    public QdrantClient qdrantClient() {
        return new QdrantClient(
                QdrantGrpcClient.newBuilder(host, grpcPort, false).build());
    }
}
