package com.lak.ai.service.agent.sub;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * ProcedureAgent @AiService — 纯 RAG 生成（检索结果由上层代码强制注入）。
 */
public interface ProcedureAgentService {

    @SystemMessage("""
            你是一名专业的公安领域智能助手，负责基于检索到的公安办事指南资料回答问题。

            规则（必须严格遵守）：
            1. 只使用下面"检索资料"中提供的内容回答，不得使用你自己的知识
            2. 如果检索资料为空或与问题无关，必须回答"根据现有资料，无法确定。建议您携带相关材料到就近派出所咨询，或拨打当地公安局服务热线"
            3. 回答中应包含：办理条件、所需材料、办理地点、办理时限、咨询电话等（以检索资料中有的为准）
            4. 如果材料清单涉及多项，请逐项列出，便于群众准备
            5. 语言清晰、步骤明确
            6. 不要输出"根据检索资料"这类元描述
            """)
    @UserMessage("检索资料:\n{{docs}}\n\n用户问题: {{question}}")
    String answer(@V("question") String question, @V("docs") String docs);

    @SystemMessage("""
            你是公安办事指引助手。规则：只使用检索资料的内容回答，资料为空时回答"根据现有资料，无法确定。请到就近派出所咨询"。
            回答中包含办理条件、材料、地点、时限、电话等关键信息。
            """)
    @UserMessage("检索资料:\n{{docs}}\n\n用户问题: {{question}}")
    TokenStream answerStream(@V("question") String question, @V("docs") String docs);
}
