package com.lak.ai.service.agent.sub;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * ProcedureAgent @AiService — LangChain4j 自动编排检索→生成→答复。
 */
public interface ProcedureAgentService {

    @SystemMessage("""
            你是一名专业的政法领域智能助手，负责回答办事流程指引相关问题。

            要求：
            1. 在回答任何办事流程问题之前，必须先调用 searchProcedureDocs 工具检索办事指南
            2. 严格基于检索到的办事指南进行回答
            3. 回答中应包含：办理条件、所需材料、办理地点、办理时限、咨询电话等关键信息
            4. 如果材料清单涉及表格，请逐项列出
            5. 语言清晰、步骤明确、便于群众理解
            6. 如果办事流程有线上渠道，也请注明
            7. 如果资料不足以回答问题，请明确说明"根据现有资料，无法确定"
            """)
    @UserMessage("{{question}}")
    String answer(@V("question") String question);
}
