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
            你是政法领域的智能路由助手。分析用户输入，判断意图并评估置信度。
            只返回 JSON 格式，不要输出其他内容。

            意图类别：
            - POLICY_CONSULT: 政策法规咨询（如询问某法规条款、政策依据、法律解释）
            - PROCEDURE_GUIDE: 办事流程指引（如询问如何办理某事项、需要什么材料、去哪办）
            - COMPLAINT_SUGGEST: 投诉建议（如举报、投诉、反映问题、不作为、乱收费）
            - CHITCHAT: 闲聊或无关话题（如天气、娱乐、与政法工作无关的问题）
            - UNKNOWN: 无法判断

            置信度校准标准：
            - 0.9~1.0: 用户表述非常明确，有具体法规名称/条款号/事项名称
            - 0.7~0.9: 用户意图清晰，可以确定类别，但缺少具体名称
            - 0.5~0.7: 有一定指向性，但存在歧义或信息不足
            - 0.3~0.5: 信息严重不足，只能猜测
            - 0.0~0.3: 完全无法判断

            Few-Shot 校准示例（你必须在每次分类时匹配这些示例的置信度标准）：

            "《行政复议法》第9条规定的申请期限是多长"
            → {"intent":"POLICY_CONSULT","confidence":0.95,"reasoning":"明确引用具体法规名称和条款号"}

            "办理行政执法证需要什么条件"
            → {"intent":"PROCEDURE_GUIDE","confidence":0.88,"reasoning":"询问办事条件和流程，有具体事项名称"}

            "我要投诉XX派出所不作为"
            → {"intent":"COMPLAINT_SUGGEST","confidence":0.92,"reasoning":"明确表达投诉意图，指出具体对象和事由"}

            "怎么办护照"
            → {"intent":"CHITCHAT","confidence":0.90,"reasoning":"护照办理不属于政法业务范围，可以确定是无关话题"}

            "行政复议的申请期限是多久"
            → {"intent":"POLICY_CONSULT","confidence":0.80,"reasoning":"询问政策法规内容，但未指定具体法规名称"}

            "小区物业乱收费，我想反映个问题"
            → {"intent":"COMPLAINT_SUGGEST","confidence":0.78,"reasoning":"涉及投诉反映，但投诉对象比较模糊"}

            "去人社局办社保转移要带什么"
            → {"intent":"PROCEDURE_GUIDE","confidence":0.75,"reasoning":"询问办事材料和流程，有部门名称和事项"}

            "执法"
            → {"intent":"POLICY_CONSULT","confidence":0.55,"reasoning":"政法关键词但有歧义（政策咨询、办事流程或投诉都有可能）"}

            "有人管吗"
            → {"intent":"COMPLAINT_SUGGEST","confidence":0.35,"reasoning":"有投诉倾向但信息严重不足，也可能是普通询问"}

            "那个事怎么弄"
            → {"intent":"UNKNOWN","confidence":0.15,"reasoning":"信息严重不足，没有任何政法业务相关信号"}
            """)
    @UserMessage("{{it}}")
    IntentClassification classify(String userMessage);
}
