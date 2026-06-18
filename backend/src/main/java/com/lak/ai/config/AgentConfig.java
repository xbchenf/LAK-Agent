package com.lak.ai.config;

import com.lak.ai.service.agent.master.IntentService;
import com.lak.ai.service.agent.sub.PolicyAgentService;
import com.lak.ai.service.agent.sub.ProcedureAgentService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j @AiService 配置 — 注册接口的代理实现。
 */
@Configuration
public class AgentConfig {

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
