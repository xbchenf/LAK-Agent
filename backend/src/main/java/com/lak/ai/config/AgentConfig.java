package com.lak.ai.config;

import com.lak.ai.service.agent.master.IntentService;
import com.lak.ai.service.agent.sub.PolicyAgentService;
import com.lak.ai.service.agent.sub.PolicyAgentTools;
import com.lak.ai.service.agent.sub.ProcedureAgentService;
import com.lak.ai.service.agent.sub.ProcedureAgentTools;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j Agent 配置 — 注册 @AiService 实现。
 */
@Configuration
public class AgentConfig {

    /**
     * IntentService — 意图分类（替换手写 IntentClassifier 的 Prompt 加载 + JSON 解析）。
     */
    @Bean
    public IntentService intentService(ChatModel chatModel) {
        return AiServices.builder(IntentService.class)
                .chatModel(chatModel)
                .build();
    }

    @Bean
    public PolicyAgentService policyAgentService(ChatModel chatModel, PolicyAgentTools tools) {
        return AiServices.builder(PolicyAgentService.class)
                .chatModel(chatModel)
                .tools(tools)
                .build();
    }

    @Bean
    public ProcedureAgentService procedureAgentService(ChatModel chatModel, ProcedureAgentTools tools) {
        return AiServices.builder(ProcedureAgentService.class)
                .chatModel(chatModel)
                .tools(tools)
                .build();
    }
}
