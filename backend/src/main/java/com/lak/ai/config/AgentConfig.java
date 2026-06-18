package com.lak.ai.config;

import com.lak.ai.service.agent.master.IntentService;
import com.lak.ai.service.agent.sub.PolicyAgentService;
import com.lak.ai.service.agent.sub.ProcedureAgentService;
import dev.langchain4j.community.model.dashscope.QwenChatModel;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j 配置 — ChatModel / EmbeddingModel Bean + @AiService 注册。
 */
@Configuration
public class AgentConfig {

    @Bean
    public ChatModel chatModel(@Value("${DASHSCOPE_API_KEY}") String apiKey) {
        return QwenChatModel.builder().apiKey(apiKey).modelName("qwen3.7-max").build();
    }

    @Bean
    public EmbeddingModel embeddingModel(@Value("${DASHSCOPE_API_KEY}") String apiKey) {
        return QwenEmbeddingModel.builder().apiKey(apiKey).modelName("text-embedding-v4").build();
    }

    @Bean
    public QdrantEmbeddingStore qdrantEmbeddingStore() {
        return QdrantEmbeddingStore.builder()
                .host("localhost").port(6334)
                .collectionName("lak_policy_docs").build();
    }

    @Bean
    public IntentService intentService(ChatModel chatModel) {
        return AiServices.builder(IntentService.class).chatModel(chatModel).build();
    }

    @Bean
    public PolicyAgentService policyAgentService(ChatModel chatModel) {
        return AiServices.builder(PolicyAgentService.class).chatModel(chatModel).build();
    }

    @Bean
    public ProcedureAgentService procedureAgentService(ChatModel chatModel) {
        return AiServices.builder(ProcedureAgentService.class).chatModel(chatModel).build();
    }
}
