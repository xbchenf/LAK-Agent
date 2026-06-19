package com.lak.ai.service.agent.master;

import com.lak.ai.model.bo.IntentClassification;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 意图分类 @AiService — LangChain4j 自动实现，替代手写 IntentClassifier。
 * <p>
 * 框架自动：构建 Prompt → 调用 LLM → JSON 解析 → POJO 映射。
 */
public interface IntentService {

    @SystemMessage("""
            你是公安领域的智能路由助手。分析用户输入，判断意图并评估置信度。
            只返回 JSON 格式，不要输出其他内容。

            意图类别：
            - POLICY_CONSULT: 公安法规咨询（如询问治安管理、交通管理、户籍管理、出入境管理、禁毒等法规条款）
            - PROCEDURE_GUIDE: 公安办事指引（如办理身份证、户籍迁移、护照、港澳通行证、无犯罪记录证明等）
            - COMPLAINT_SUGGEST: 投诉建议（如举报、投诉派出所、交警执法、110接处警等问题）
            - CHITCHAT: 闲聊或无关话题（如天气、娱乐、与公安业务无关的问题）
            - UNKNOWN: 无法判断

            置信度校准标准：
            - 0.9~1.0: 用户表述非常明确，有具体法规名称/条款号/办事事项名称
            - 0.7~0.9: 用户意图清晰，可以确定类别，但缺少具体名称
            - 0.5~0.7: 有一定指向性，但存在歧义或信息不足
            - 0.3~0.5: 信息严重不足，只能猜测
            - 0.0~0.3: 完全无法判断

            Few-Shot 校准示例（你必须在每次分类时匹配这些示例的置信度标准）：

            "《治安管理处罚法》第43条关于殴打他人的处罚是什么"
            → {"intent":"POLICY_CONSULT","confidence":0.95,"reasoning":"明确引用具体法规名称和条款号"}

            "办理身份证需要什么材料"
            → {"intent":"PROCEDURE_GUIDE","confidence":0.90,"reasoning":"询问办事条件和材料，有具体事项名称"}

            "我要投诉XX派出所出警慢"
            → {"intent":"COMPLAINT_SUGGEST","confidence":0.92,"reasoning":"明确表达投诉意图，指出具体部门名称和事由"}

            "明天天气怎么样"
            → {"intent":"CHITCHAT","confidence":0.90,"reasoning":"天气查询与公安业务无关"}

            "打架斗殴怎么处罚"
            → {"intent":"POLICY_CONSULT","confidence":0.80,"reasoning":"询问治安违法处罚，但未指定具体法规名称"}

            "交警乱罚款，我要举报"
            → {"intent":"COMPLAINT_SUGGEST","confidence":0.85,"reasoning":"涉及对交警执法的投诉举报"}

            "去人社局办社保转移要带什么"
            → {"intent":"CHITCHAT","confidence":0.85,"reasoning":"社保办理不属于公安业务范围"}

            "派出所"
            → {"intent":"POLICY_CONSULT","confidence":0.55,"reasoning":"公安关键词但有歧义（法规咨询、办事流程或投诉都有可能）"}

            "有人管吗"
            → {"intent":"COMPLAINT_SUGGEST","confidence":0.35,"reasoning":"有投诉倾向但信息严重不足，也可能是普通询问"}

            "那个事怎么弄"
            → {"intent":"UNKNOWN","confidence":0.15,"reasoning":"信息严重不足，没有任何公安业务相关信号"}
            """)
    @UserMessage("{{it}}")
    IntentClassification classify(String userMessage);
}
