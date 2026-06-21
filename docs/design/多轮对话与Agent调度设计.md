# 多轮对话与Agent调度设计

> **项目名称**：LAK-Agent（Legal Affairs Knowledge Agent Platform / 政法智能知识Agent平台）
> **文档版本**：v1.0
> **编制日期**：2026-06-21
> **文档状态**：定稿
> **密级**：内部

---

## 目录

1. [概述](#1-概述)
2. [主Agent — 意图识别与路由](#2-主agent--意图识别与路由)
3. [子Agent体系](#3-子agent体系)
4. [子Agent调度器](#4-子agent调度器)
5. [会话状态机](#5-会话状态机)
6. [上下文窗口管理](#6-上下文窗口管理)
7. [Slot-Filling 引擎](#7-slot-filling-引擎)
8. [流式输出 (SSE)](#8-流式输出-sse)
9. [合规校验器](#9-合规校验器)
10. [ChatService 全链路编排](#10-chatservice-全链路编排)
11. [配置参数速查](#11-配置参数速查)

---

## 1. 概述

### 1.1 编排架构

LAK-Agent 采用**主Agent + 多子Agent**的编排架构，核心流程为：

```
用户消息
  → ChatService (会话管理 + 全链路编排)
    → MasterAgent.route() (意图识别 + 置信度判断 + 路由决策)
      → IntentClassifier (LLM 意图分类)
      → ConfidenceEvaluator (3层置信度计算)
      → RouteDispatcher (路由决策生成)
    → SubAgentScheduler.dispatch() (子Agent调度)
      → PolicyAgent / ProcedureAgent / ComplaintAgent
        → RAG 检索 + LLM 生成
    → ComplianceValidator (合规校验)
    → 返回给用户
```

### 1.2 核心组件

| 组件 | 类名 | 职责 |
|------|------|------|
| 对话服务 | `ChatService` | 全链路编排（JSON + SSE 两种模式） |
| 主Agent | `MasterAgent` | 意图识别与路由编排 |
| 意图分类器 | `IntentClassifier` | 调用 LLM 进行意图分类 |
| 置信度评估器 | `ConfidenceEvaluator` | 3层置信度融合 |
| 路由分发器 | `RouteDispatcher` | 意图→子Agent路由决策 |
| 兜底处理器 | `FallbackHandler` | 低置信度人工客服引导 |
| 子Agent调度器 | `SubAgentScheduler` | 子Agent注册与调度 |
| 会话管理器 | `SessionManager` | 会话生命周期（Redis + MySQL） |
| 上下文窗口 | `ContextWindow` | 对话历史管理（最近10轮） |
| 槽位填充引擎 | `SlotFillingEngine` | 投诉场景多轮信息采集 |
| 合规校验器 | `ComplianceValidator` | AI答复安全合规审查 |

---

## 2. 主Agent — 意图识别与路由

### 2.1 MasterAgent 编排流程

```java
// MasterAgent.route()
public RoutingDecisionBO route(AgentRequest request) {
    // Step 1: 意图分类 (LLM)
    ClassificationResult classification = intentClassifier.classify(message);

    // Step 2: 置信度评估 (结构化输出 + 规则加权 + 降级)
    ConfidenceResult confidence = confidenceEvaluator.evaluate(message, classification);

    // Step 3: 路由决策
    RoutingDecisionBO decision = routeDispatcher.dispatch(confidence);

    // Step 4: 追加用户消息到上下文窗口
    contextWindow.append(sessionId, "user", message);

    return decision;
}
```

### 2.2 IntentClassifier — LLM 意图分类

通过 LangChain4j `@AiService` 接口调用 LLM，以结构化 JSON 输出意图分类结果。

**调用方式**：

```java
// IntentService (LangChain4j @AiService)
@SystemMessage("""
    你是政法领域的智能路由助手。分析用户输入，判断意图并评估置信度。
    以 JSON 格式返回，不要输出其他内容。

    意图类别：POLICY_CONSULT / PROCEDURE_GUIDE / COMPLAINT_SUGGEST / CHITCHAT / UNKNOWN

    置信度校准标准：
    - 0.9~1.0: 用户表述非常明确，有具体法规名称/条款号/事项名称
    - 0.7~0.9: 用户意图清晰，但缺少具体名称
    - 0.5~0.7: 有一定指向性，但存在歧义
    - 0.3~0.5: 信息严重不足，只能猜测
    - 0.0~0.3: 完全无法判断

    [10个 Few-Shot 示例...]
    """)
@UserMessage("{{it}}")
IntentClassification classify(String userMessage);
```

**容错处理**：
- `IntentType.valueOf()` 解析失败 → 降级为 `UNKNOWN`
- LLM 调用异常 → 返回 `UNKNOWN` + 置信度 0.0
- LLM 返回空响应 → 返回 `UNKNOWN` + 置信度 0.0

### 2.3 ConfidenceEvaluator — 3层置信度评估

```
Layer 1: 响应校验
  - LLM 返回空/异常 → UNKNOWN, confidence=0.0, fallback=true
  - UNKNOWN 意图 → 保持模型原始置信度, fallback=true

Layer 2: 硬规则（政法领域先验，绕过 LLM）
  ┌─────────────────────────────────────────────────────┐
  │ 发文字号精确命中: "X政发〔20XX〕XX号" 模式            │
  │   → intent=POLICY_CONSULT, confidence≥0.85          │
  │                                                     │
  │ 投诉关键词命中: "我要投诉" / "我要举报" / "不作为" 等  │
  │   → intent=COMPLAINT_SUGGEST, confidence≥0.85       │
  └─────────────────────────────────────────────────────┘

Layer 3: 规则加权 (模型置信度 + 规则调整)
  ┌─────────────────────────────────────────────────────┐
  │ 政法术语 Boost: 命中 30 个公安政法高频术语中的任意     │
  │   → confidence + 0.05                               │
  │                                                     │
  │ 结构标记 Boost: 命中 "第X条" / "第X章" 等            │
  │   → confidence + 0.08                               │
  │                                                     │
  │ 消息过短惩罚: 消息 < 5 字且无政法术语                 │
  │   → confidence - 0.10                               │
  └─────────────────────────────────────────────────────┘

最终判定:
  confidence ≥ 0.7  → 直接路由至对应子Agent
  confidence 0.5~0.7 → needsVoting=true（预留给 Self-Consistency 投票扩展）
  confidence < 0.5  → fallback=true
```

**政法术语词表**（30个高频术语，硬编码在 `ConfidenceEvaluator` 中）：

```
行政复议, 行政诉讼, 行政处罚, 行政许可, 行政强制, 工伤认定,
执法程序, 治安管理, 户籍管理, 身份证, 居住证, 无犯罪记录,
投诉, 举报, 不作为, 乱收费, 劳动争议, 社会保险,
调解, 仲裁, 法律援助, 司法鉴定, 公证, 律师,
派出所, 公安局, 法院, 检察院, 司法所, 社区矫正
```

### 2.4 RouteDispatcher — 路由决策

```java
public RoutingDecisionBO dispatch(ConfidenceResult result) {
    if (result.fallback()) {
        return RoutingDecisionBO.builder()
            .intentType(result.intentType())
            .confidence(result.confidence())
            .targetAgentId(null)     // null = 兜底
            .fallback(true)
            .build();
    }
    // 正常路由
    String targetAgentId = result.intentType().getTargetAgentId();
    if (targetAgentId == null) {  // CHITCHAT / UNKNOWN
        return fallback decision;
    }
    return RoutingDecisionBO.builder()
        .intentType(result.intentType())
        .confidence(result.confidence())
        .targetAgentId(targetAgentId)
        .fallback(false)
        .build();
}
```

### 2.5 FallbackHandler — 兜底处理

两种预设模板，不调用大模型：

| 场景 | 触发条件 | 模板内容 |
|------|---------|---------|
| **低置信度** | confidence < 0.5 | 引导用户重新表述问题，提供人工客服联系方式 |
| **闲聊** | intent = CHITCHAT | 介绍助手能力范围，建议有效的咨询方向 |

```java
// LOW_CONFIDENCE 模板
"感谢您的咨询。为确保给您提供准确的信息，建议您重新表述问题。
 您也可以通过以下方式联系我们：服务热线 110 / 当地派出所。"

// CHITCHAT 模板
"我是政法智能问答助手，专注于公安政策法规咨询和办事流程指引。
 您可向我咨询：政策法规解读、办事流程指引、投诉建议提交。"
```

> **安全约束**：兜底处理**禁止调用大模型生成答复**，仅使用预定义模板。这是政法场景的合规底线 — 不确定时宁可拒答，不可猜测。

---

## 3. 子Agent体系

### 3.1 SubAgent 接口

```java
public interface SubAgent {
    /** Agent 唯一标识 */
    String getAgentId();

    /** Agent 名称 */
    String getAgentName();

    /** 支持的意图类别（取第一个元素匹配路由） */
    IntentType[] getSupportedIntents();

    /** 执行业务处理 */
    AgentResponse process(AgentRequest request);
}
```

### 3.2 子Agent注册表

| Agent ID | Agent 名称 | 支持意图 | 核心能力 | 实现类 |
|----------|-----------|---------|---------|--------|
| `agent-policy` | 政策咨询Agent | POLICY_CONSULT | RAG检索(lak_policy_docs) + LLM生成 | `PolicyAgent` |
| `agent-procedure` | 办事指引Agent | PROCEDURE_GUIDE | RAG检索(lak_procedure_docs) + LLM生成 | `ProcedureAgent` |
| `agent-complaint` | 投诉建议Agent | COMPLAINT_SUGGEST | 返回投诉引导页链接 | `ComplaintAgent` |

### 3.3 PolicyAgent — 政策咨询

**两阶段强制流水线**（保证 LLM 始终有检索上下文）：

```
Step 1: tools.search(message)
  → HybridRetriever.search(query, COLLECTION_POLICY)
  → SourceTracer.buildCitations(fragments)
  → 格式化为引用文本:
    【sourceNo 第articleNo条】(生效: effectiveDate)
    fragment_text
    ---

Step 2: agentService.answer(message, formattedDocs)
  → PolicyAgentService (LangChain4j @AiService)
  → System Prompt: "公安政法领域助手，仅使用提供的检索材料作答，
     引用时注明文档编号和条款号，勿做元描述"
  → 返回 AI 答复

Step 3: 构建 AgentResponse
  → answer + sources(SourceCitation.toMap()) + confidence=0.9
```

**工具类** (`PolicyAgentTools`)：

```java
public class PolicyAgentTools {
    @Tool("搜索政法政策法规知识库")
    String search(String query);
}
```

### 3.4 ProcedureAgent — 办事指引

流程同 PolicyAgent，差异点：

| 维度 | PolicyAgent | ProcedureAgent |
|------|-----------|---------------|
| Qdrant Collection | `lak_policy_docs` | `lak_procedure_docs` |
| System Prompt | "公安政法领域助手，引用文档编号和条款号" | "办事指引助手，包含条件、材料、地点、时限、电话" |
| 引用格式 | `【sourceNo 第articleNo条】` | `【docTitle】` |
| SourceTracer | ✅ 使用 | ❌ 不使用（办事指南无发文字号/条号） |
| AiService | `PolicyAgentService` | `ProcedureAgentService` |

### 3.5 ComplaintAgent — 投诉建议

**不使用 AI**，直接返回静态 HTML 引导模板：

```html
<div class="complaint-guide">
  <h3>投诉建议</h3>
  <p>请通过以下方式提交您的诉求：</p>
  <ol>
    <li>在线提交：<a href="/tickets">工单系统</a></li>
    <li>电话投诉：110 / 当地派出所电话</li>
    <li>现场投诉：前往就近派出所</li>
  </ol>
</div>
```

> **设计决策**：投诉建议场景的实际信息采集由前端 TicketView + 后端 `SlotFillingEngine` 完成，Agent 层仅负责引导用户到正确的入口。这避免了在 Agent 层耦合复杂的多轮对话逻辑。

### 3.6 子Agent错误处理

所有子Agent的 `process()` 方法统一捕获异常，返回安全兜底：

```java
try {
    // 正常流程
} catch (Exception e) {
    log.error("子Agent处理失败", e);
    return AgentResponse.builder()
        .answer("系统繁忙，请稍后重试")
        .confidence(0.0)
        .build();
}
```

---

## 4. 子Agent调度器

### 4.1 SubAgentScheduler

基于 Spring Bean 自动发现机制，实现策略模式调度：

```java
@Service
public class SubAgentScheduler {
    // 构造函数：收集所有 SubAgent Bean → 构建 IntentType → SubAgent 映射
    public SubAgentScheduler(List<SubAgent> agents) {
        for (SubAgent agent : agents) {
            for (IntentType intent : agent.getSupportedIntents()) {
                registry.putIfAbsent(intent, agent);  // 重复意图 → 先注册的优先
            }
        }
    }

    /** 按意图类型调度 */
    public AgentResponse dispatch(IntentType intentType, AgentRequest request) {
        SubAgent agent = registry.get(intentType);
        if (agent == null) return null;
        return agent.process(request);
    }
}
```

### 4.2 扩展新子Agent的步骤

架构预留了便捷的扩展机制，新增子Agent只需 3 步：

```
Step 1: 创建 XxxAgent implements SubAgent
  - getAgentId() → "agent-xxx"
  - getSupportedIntents() → [IntentType.XXX]
  - process() → 编排业务逻辑

Step 2: IntentType 枚举新增 XXX
  - 定义 targetAgentId = "agent-xxx"

Step 3: 主Agent Prompt 补充新意图的描述与 Few-Shot 示例
  - 文件位置: IntentService.java 的 @SystemMessage
```

Spring 自动装配：添加 `@Component` 注解后，`SubAgentScheduler` 自动发现并注册。

---

## 5. 会话状态机

### 5.1 8状态模型

```
                  ┌─────────┐
       用户发起对话 │   NEW   │
                  └────┬────┘
                       │ 首条消息到达
                       ▼
                  ┌─────────┐
                  │ INTENT  │ 主Agent意图识别
                  │ _CHECK  │
                  └────┬────┘
                       │
            ┌──────────┼──────────┐
            ▼          ▼          ▼
       ┌─────────┐ ┌────────┐ ┌─────────┐
       │ANSWERING│ │COLLECT │ │ FALLBACK│
       │(政策/指引)│ │_INFO  │ │ (兜底)   │
       └────┬────┘ │(投诉补齐)│ └────┬────┘
            │      └───┬────┘      │
            │          │ 补齐完成   │
            ▼          ▼           │
       ┌─────────┐ ┌────────┐     │
       │COMPLIANCE│ │TICKET │     │
       │_CHECK  │ │_SUBMIT│     │
       └────┬────┘ └───┬────┘     │
            │          │          │
            ▼          ▼          ▼
       ┌─────────────────────────────┐
       │          CLOSED             │
       └─────────────────────────────┘
```

| 状态 | 触发条件 | 行为 |
|------|---------|------|
| **NEW** | 会话创建 | 分配 sessionId（UUID），写入 Redis Hash + MySQL |
| **INTENT_CHECK** | 首条消息到达 | 调用 MasterAgent.route() |
| **ANSWERING** | 置信度 ≥ 0.7（政策/指引） | 调用 RAG + LLM 生成答复 |
| **COLLECT_INFO** | 投诉意图 | 进入 Slot-Filling 多轮信息采集 |
| **FALLBACK** | 置信度 < 0.5 或 CHITCHAT | 兜底答复，引导人工客服 |
| **COMPLIANCE_CHECK** | 答复生成完成 | 合规校验器审查 |
| **TICKET_SUBMIT** | 投诉信息补齐完成 | 调用工单接口创建工单 |
| **CLOSED** | 用户主动关闭 / 会话超时 | Redis 会话 TTL 过期(30min) |

### 5.2 SessionManager — 双写策略

```
Redis (主存储，热数据):
  Key:   session:{sessionId}
  Type:  Hash
  Fields: userId, status, intentType, confidence, createTime, lastActive, contextWindow
  TTL:   1800s

MySQL (持久化，冷数据):
  Table: chat_session
  字段:   id, session_id, user_id, status, intent_type, confidence, create_time, update_time
```

**核心方法**：

| 方法 | Redis 操作 | MySQL 操作 |
|------|-----------|-----------|
| `create(userId)` | HSET 创建 Hash + EXPIRE | INSERT |
| `transition(sessionId, status)` | HSET status + EXPIRE | — |
| `setIntent(sessionId, intent, confidence)` | HSET intentType + confidence | — |
| `getStatus(sessionId)` | HGET status | — |
| `isActive(sessionId)` | 检查存在 && status ≠ CLOSED | — |
| `close(sessionId)` | HSET status=CLOSED | UPDATE status=CLOSED |
| `listSessions(userId, page, size)` | — | SELECT (排除 CLOSED) |
| `touch(sessionId)` | HSET lastActive + EXPIRE | — |

### 5.3 超时与自动关闭

- Redis TTL = 1800s（30分钟）
- 每次操作（transition / setIntent / touch / append context）刷新 TTL
- TTL 到期后 Redis 自动删除，会话视为 CLOSED
- MySQL 中的记录保留为历史数据

---

## 6. 上下文窗口管理

### 6.1 ContextWindow

存储在 Redis Hash 的 `contextWindow` 字段中，JSON 序列化。

**核心参数**：

| 参数 | 值 | 常量 |
|------|-----|------|
| 最大保留轮数 | 10 轮（20 条消息） | `MAX_CONTEXT_ROUNDS = 10` |
| 送入 LLM 的最大 Token | 6000 tokens | `MAX_CONTEXT_TOKENS = 6000` |

### 6.2 上下文管理策略

```
append(sessionId, role, content):
  1. 从 Redis 读取 contextWindow JSON
  2. 追加新消息 {role, content, timestamp}
  3. 若消息数 > 20 (10轮 × 2)，移除最早的消息
  4. 写回 Redis

buildPromptContext(sessionId):
  1. 从 Redis 读取 contextWindow
  2. 从最新消息开始累积 Token（粗估: 中文 1 字 ≈ 1 token, 英文 4 字符 ≈ 1 token）
  3. 累积至 6000 token 上限停止
  4. 返回选中消息的文本拼接（按时序排列）
```

**Token 估算算法**：

```java
int estimateTokens(String text) {
    int chineseChars = 0;
    int otherChars = 0;
    for (char c : text.toCharArray()) {
        if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
            chineseChars++;
        } else {
            otherChars++;
        }
    }
    return chineseChars + (otherChars / 4);  // 中文 1:1, 英文 4:1
}
```

### 6.3 上下文内容结构

```json
[
  {"role": "user", "content": "行政复议的时效是多久？", "timestamp": "2026-06-21T10:00:00"},
  {"role": "assistant", "content": "根据《行政复议法》第九条...", "timestamp": "2026-06-21T10:00:05"},
  {"role": "user", "content": "如果超期了怎么办？", "timestamp": "2026-06-21T10:01:00"}
]
```

---

## 7. Slot-Filling 引擎

### 7.1 槽位定义

投诉建议场景的 5 个槽位：

| 序号 | 槽位名 | 类型 | 必填 | 追问话术 | 校验规则 |
|------|--------|------|------|---------|---------|
| 1 | `complaintType` | 枚举 | ✅ | "请问您要反馈的是哪类问题？（治安问题/窗口服务/警务人员/其他）" | — |
| 2 | `contactName` | 文本 | ✅ | "请留下您的称呼：" | — |
| 3 | `contactPhone` | 文本 | ✅ | "请留下您的联系电话：" | `^1[3-9]\d{9}$` (手机号) |
| 4 | `description` | 长文本 | ✅ | "请详细描述您的问题：" | — |
| 5 | `attachment` | 文本 | ❌ | "如有相关材料可上传（可选）：" | — |

### 7.2 状态存储

Redis Hash 字段：
- `slotValues` — JSON Map `{complaintType: "治安问题", contactName: "张三", ...}`
- `currentSlot` — 当前采集的槽位序号 (0-4)
- `fillRound` — 已进行的采集轮数

### 7.3 采集流程

```
startFilling(sessionId):
  1. 清空 slotValues, currentSlot=0, fillRound=1
  2. transition COLLECT_INFO
  3. 返回第 1 个槽位的追问话术

processResponse(sessionId, userMessage):
  1. 获取当前槽位
  2. 若有校验规则且不匹配 → retry（返回校验提示，不消耗轮数）
  3. 存储值到 slotValues
  4. 跳到下一个必填槽位（跳过可选槽位）
  5. 全部必填槽位采集完毕 → 检查可选槽位 → 若有未填可选槽位，切换到该槽位
  6. 全部槽位完成 → transition TICKET_SUBMIT → allDone(slotValues)
  7. fillRound > 5 → timeout（"信息采集超时，请通过电话联系我们"）
```

### 7.4 FillingResult

```java
public record FillingResult(
    boolean done,          // 是否全部完成
    String nextPrompt,     // 下一个追问话术
    String retrySlot,      // 需重试的槽位名（校验失败时）
    String retryMessage,   // 校验失败提示
    Map<String, String> slotValues  // 当前已采集的值
) {
    static FillingResult nextSlot(String prompt);     // 继续下一个槽位
    static FillingResult retry(String slot, String msg); // 校验失败，重试当前槽位
    static FillingResult allDone(Map<String, String> values); // 全部完成
    static FillingResult timeout();                   // 超时
}
```

---

## 8. 流式输出 (SSE)

### 8.1 SSE 触发条件

`ChatController` 检查请求的 `Accept` header：
- `Accept: text/event-stream` → 进入 SSE 流式模式
- 其他 → 进入 JSON 同步模式

### 8.2 SSE 流式处理流程

```
ChatService.processMessageStream(userId, sessionId, message)
  │
  ├─ Step 1-3: 同 JSON 模式 — 主Agent路由决策
  │
  ├─ Step 4: 路由分发
  │   ├─ FALLBACK → 发送单条 SSE message 事件 → done 事件
  │   ├─ POLICY_CONSULT → PolicyAgentService.answerStream() [非阻塞线程]
  │   ├─ PROCEDURE_GUIDE → ProcedureAgentService.answerStream() [非阻塞线程]
  │   └─ COMPLAINT → 非流式 dispatch → SSE message 事件
  │
  └─ Step 5: TokenStream 回调
      ├─ onPartialResponse(token) → SSE "message" 事件 (逐 Token)
      ├─ onCompleteResponse(fullAnswer) → SSE "done" 事件
      │   → 保存完整答复到上下文窗口 → 合规校验
      └─ onError → SSE "error" 事件 → completeWithError
```

### 8.3 SSE 事件格式

```
event: message
data: {"token": "根据", "index": 0}

event: message
data: {"token": "《行政复议法》", "index": 1}

...

event: done
data: {"sessionId": "...", "confidence": 0.9}

event: error
data: {"message": "模型调用超时"}
```

### 8.4 SSE 超时控制

| 参数 | 值 |
|------|-----|
| 单次 SSE 连接超时 | 120s |
| 执行线程 | 独立线程（非 Tomcat request thread） |
| 超时后行为 | `completeWithError`，前端显示"连接中断" |

### 8.5 TokenStream 接口

```java
// LangChain4j StreamingChatLanguageModel 的 TokenStream
@AiService
public interface PolicyAgentService {
    @SystemMessage("公安政法领域助手...")
    TokenStream answerStream(@UserMessage String question, @V("docs") String docs);
}

// 使用方
TokenStream tokenStream = policyAgentService.answerStream(question, docs);
tokenStream.onPartialResponse(partial -> {
    // 逐 Token 发射 SSE message 事件
}).onCompleteResponse(response -> {
    // 发射 SSE done 事件
}).onError(error -> {
    // 发射 SSE error 事件
}).start();
```

---

## 9. 合规校验器

### 9.1 ComplianceValidator

在 AI 答复返回用户之前，执行 4 项安全检查：

```
AI答复 → [1]空值检查 → [2]长度检查 → [3]敏感词检查 → [4]溯源检查 → 放行
            │             │              │              │
            ▼             ▼              ▼              ▼
         拒绝         拒绝(长度<5)    拒绝(含敏感词)   拒绝(无溯源)
```

| 检查项 | 规则 | 不通过处理 |
|--------|------|-----------|
| **空值检查** | 答复不为 null 或 blank | 替换为"系统繁忙，请稍后重试" |
| **长度检查** | 答复长度 ≥ 5 字符 | 同上 |
| **敏感词检查** | 答复不含敏感词（复用 SensitiveWordPreCheckFilter 词库） | 同上，记录告警日志 |
| **溯源检查** | 非 FALLBACK 且提供了检索片段时，至少一条同时具备 `docId` 和 `sourceNo` | 同上 |

### 9.2 溯源校验的例外场景

以下场景**豁免**溯源校验：
- **FALLBACK 答复**：兜底答复本身不依赖知识库
- **ComplaintAgent 答复**：投诉引导不涉及知识检索
- **未提供检索片段**：Agent 未调用 RAG 的情况（如闲聊兜底）

### 9.3 合规失败的处理

```java
// ChatService.enforceCompliance()
if (!complianceValidator.validate(response, fragments, isFallback)) {
    log.warn("合规校验失败, sessionId={}, originalAnswer={}", sessionId, answer);
    response.setAnswer("系统繁忙，请稍后重试");  // 安全兜底答复
    response.setSources(Collections.emptyList());
}
```

---

## 10. ChatService 全链路编排

### 10.1 JSON 模式完整时序

```
ChatController.sendMessage()
  │
  ▼
ChatService.processMessage(userId, sessionId, message)
  │
  ├─ [会话管理] 无 sessionId → SessionManager.create(userId)
  ├─ [状态检查] SessionManager.isActive(sessionId)
  ├─ [上下文] ContextWindow.getContext(sessionId)
  │
  ├─ [意图识别] session → INTENT_CHECK
  │   MasterAgent.route(AgentRequest)
  │   ├─ IntentClassifier.classify(message)
  │   ├─ ConfidenceEvaluator.evaluate(message, classification)
  │   └─ RouteDispatcher.dispatch(confidenceResult)
  │
  ├─ [路由分发]
  │   ├─ fallback=true → MasterAgent.fallback()
  │   │   └─ FallbackHandler.handle() → 人工客服引导模板
  │   │
  │   └─ fallback=false → session → ANSWERING
  │       └─ SubAgentScheduler.dispatch(intentType, request)
  │           ├─ PolicyAgent.process()   (POLICY_CONSULT)
  │           ├─ ProcedureAgent.process() (PROCEDURE_GUIDE)
  │           └─ ComplaintAgent.process() (COMPLAINT_SUGGEST)
  │
  ├─ [合规校验] session → COMPLIANCE_CHECK
  │   ComplianceValidator.validate(answer, fragments, isFallback)
  │   └─ 失败 → 替换为安全兜底答复
  │
  ├─ [上下文保存] ContextWindow.append(sessionId, "user", message)
  │              ContextWindow.append(sessionId, "assistant", answer)
  │
  └─ [返回] ChatResult(sessionId, answer, sources, confidence, intentType)
```

### 10.2 ChatResult

```java
public record ChatResult(
    String sessionId,
    String response,         // AI 答复文本
    RoutingDecisionBO decision, // 路由决策
    boolean error,           // 是否触发错误
    String errorMessage      // 错误信息
) {}
```

---

## 11. 配置参数速查

### 11.1 Agent 常量

```java
// AgentConstants
AGENT_POLICY = "agent-policy"
AGENT_PROCEDURE = "agent-procedure"
AGENT_COMPLAINT = "agent-complaint"
CONFIDENCE_THRESHOLD_HIGH = 0.7
CONFIDENCE_THRESHOLD_LOW = 0.5
VOTING_ROUNDS = 3
```

### 11.2 会话常量

```java
// ChatConstants
SESSION_KEY_PREFIX = "session:"
SESSION_TTL_SECONDS = 1800        // 30分钟
MAX_CONTEXT_ROUNDS = 10           // 10轮 = 20条消息
MAX_CONTEXT_TOKENS = 6000         // 送入LLM的token上限
MAX_MESSAGE_LENGTH = 2000         // 单条消息最大字符数
MAX_SLOT_FILL_ROUNDS = 5          // 最大追问轮数
```

### 11.3 application.yml 会话配置

```yaml
lak:
  session:
    ttl-seconds: 1800
    max-context-rounds: 10
    max-tokens: 6000
  confidence:
    threshold-high: 0.7
    threshold-low: 0.5
  message:
    max-length: 2000
```

### 11.4 IntentType 枚举

| 枚举值 | targetAgentId | 说明 |
|--------|-------------|------|
| `POLICY_CONSULT` | `agent-policy` | 政策咨询 |
| `PROCEDURE_GUIDE` | `agent-procedure` | 办事指引 |
| `COMPLAINT_SUGGEST` | `agent-complaint` | 投诉建议 |
| `CHITCHAT` | `null` → fallback | 闲聊/无关 |
| `UNKNOWN` | `null` → fallback | 无法识别 |

---

*文档结束。v1.0 — 2026-06-21*
