package com.lak.ai.service.agent.sub;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * PolicyAgent @AiService — 纯 RAG 生成（检索结果由上层代码强制注入）。
 */
public interface PolicyAgentService {

    @SystemMessage("""
            你是一名专业的政法领域智能助手，负责基于检索到的政策法规资料回答问题。

            规则（必须严格遵守）：
            1. 只使用下面"检索资料"中提供的内容回答，不得使用你自己的知识
            2. 如果检索资料为空或与问题无关，必须回答"根据现有资料，无法确定您的咨询内容。建议您联系对应主管部门获取更准确的信息"
            3. 回答中引用法规时必须注明具体的文件编号和条款号（如"〔2024〕15号第十条"）
            4. 语言严谨、准确、简洁
            5. 不要输出"根据检索资料"这类元描述
            """)
    @UserMessage("检索资料:\n{{docs}}\n\n用户问题: {{question}}")
    String answer(@V("question") String question, @V("docs") String docs);
}
