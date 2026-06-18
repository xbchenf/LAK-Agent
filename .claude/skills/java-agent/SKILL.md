---
name: java-agent
description: LAK-Agent 子Agent 开发规范。新增子Agent 时自动应用 SubAgent 接口 + 注册模式。
---

# Agent 开发规范（LAK-Agent）

## SubAgent 统一接口

```java
package com.lak.ai.service.agent;

public interface SubAgent {
    /** Agent 唯一标识 */
    String getAgentId();

    /** Agent 名称 */
    String getAgentName();

    /** 支持的意图类别 */
    IntentType[] getSupportedIntents();

    /** 执行业务处理 */
    AgentResponse process(AgentRequest request);
}
```

## 已有 Agent 注册表

| Agent ID | 类名 | 支持意图 | Qdrant Collection |
|----------|------|---------|-------------------|
| `agent-policy` | PolicyAgent | POLICY_CONSULT | `lak_policy_docs` |
| `agent-procedure` | ProcedureAgent | PROCEDURE_GUIDE | `lak_procedure_docs` |
| `agent-complaint` | ComplaintAgent | COMPLAINT_SUGGEST | —（使用 TicketAdapter） |

## 新增子Agent 的步骤

```java
// Step 1: 创建 Agent 实现类
@Slf4j
@Component
public class TemplateAgent implements SubAgent {

    @Override
    public String getAgentId() {
        return "agent-template";
    }

    @Override
    public String getAgentName() {
        return "文书模板查询Agent";
    }

    @Override
    public IntentType[] getSupportedIntents() {
        return new IntentType[]{IntentType.TEMPLATE_QUERY};
    }

    @Override
    public AgentResponse process(AgentRequest request) {
        // 编排能力组件：RagEngine + 大模型 + 合规校验
    }
}

// Step 2: 注册意图类型
// 在 IntentType 枚举中新增 TEMPLATE_QUERY
// 在主Agent IntentClassifier Prompt 中补充新意图描述

// Step 3: 创建 Qdrant Collection（如需RAG）
// Collection: lak_template_docs
// 双向量: Dense(1536维) + Sparse(BM25)
```

## AgentResponse 结构

```java
@Data
@Builder
public class AgentResponse {
    private String answer;           // AI 答复文本
    private List<SourceDoc> sources; // 溯源文档列表
    private double confidence;       // 最终置信度
    private String intentType;       // 意图类型
    private Map<String, Object> extra; // 扩展数据（如工单编号）
}
```

## 强制约束

- Agent 必须实现 `SubAgent` 接口，通过 Spring `@Component` 自动注册
- `process()` 方法内部必须处理异常，不能向上抛未处理异常
- AI 答复必须通过 ComplianceValidator 校验后才返回
- RAG 检索使用 HybridRetriever（混合检索），不使用纯向量检索
- 新增 Agent 涉及 3 层变更: IntentType 枚举 + SubAgent 实现 + Qdrant Collection
