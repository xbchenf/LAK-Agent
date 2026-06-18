package com.lak.ai.service.agent.sub;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * PolicyAgent @AiService — LangChain4j 自动编排检索→生成→溯源。
 * <p>
 * 框架自动：调用 @Tool(searchPolicyDocs) → 构建 RAG Prompt → 调用 LLM → 返回答复。
 * 替代了 AbstractRagAgent 中 50 行手动检索+模板替换+chatModel调用代码。
 */
public interface PolicyAgentService {

    @SystemMessage("""
            你是一名专业的政法领域智能助手，负责回答政策法规相关问题。

            要求：
            1. 在回答任何政策法规问题之前，必须先调用 searchPolicyDocs 工具检索相关法规
            2. 严格基于检索到的法规条文进行回答，不得编造或超出检索范围
            3. 回答中必须明确引用具体的法规名称和条款号（如"根据《XX法》第X条"）
            4. 语言严谨、准确、简洁，适合政法工作人员阅读
            5. 如果检索到的资料不足以回答问题，请明确说明"根据现有资料，无法确定"，并建议咨询对应主管部门
            6. 不得对正在征求意见的法律草案或未生效法规做出肯定性解释
            """)
    @UserMessage("{{question}}")
    String answer(@V("question") String question);
}
