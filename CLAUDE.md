# LAK-Agent 政法智能知识Agent平台

> Legal Affairs Knowledge Agent Platform — 面向政法行业私有化部署的智能问答平台。

## 项目概览

政法行业私有化智能问答平台，采用主Agent+多子Agent架构。统一前置拦截 -> 主Agent意图识别+置信度判断 -> 路由分发至对应子Agent -> 业务处理 -> 结果合规校验 -> 统一响应封装 + 全链路审计日志落库。低置信度场景（<0.5）自动兜底至人工客服，禁止AI自动作答。0.5-0.7 区间正常路由，预留 needsVoting 标记供后续 Self-Consistency 投票扩展。

**包名**: `com.lak.ai` | **服务名**: `lak-ai-platform` | **数据库名**: `lak_ai_platform`

## 技术栈速查

| 组件 | 版本 | 用途 |
|------|------|------|
| JDK | 17 | 运行环境 |
| Spring Boot | 3.4.2 | 核心框架 |
| LangChain4j | 1.14.0 | AI编排框架 |
| Maven | 3.8+ | 构建工具 |
| MySQL | 8.0 | 业务数据 + 审计日志 |
| Redis | 7 | 会话记忆 + 限流 |
| Qdrant | - | 向量存储（私有化部署） |
| MinIO | - | 文件存储（生产）/本地磁盘（开发） |
| Nginx | 1.24+ | 反向代理 + HTTPS终结 |
| Mybatis-Plus | - | ORM |
| Resilience4j | - | 熔断降级 |
| SpringDoc | - | 接口文档 |
| 大模型 | Qwen3.7-Max（百炼） | 对话生成 |
| 向量模型 | text-embedding-v4（百炼） | 1024维，文本向量化 |

## 项目结构

```
LAK-Agent/
├── CLAUDE.md                                  # 本文件
├── docs/design/                               # 8份设计文档
├── backend/                                    # Spring Boot 后端
│   ├── pom.xml
│   └── src/main/java/com/lak/ai/
│   ├── LakAiApplication.java
│   ├── controller/                              # 控制器层
│   │   ├── AuthController                       #   /api/v1/auth/*
│   │   ├── ChatController                       #   /api/v1/chat/*
│   │   ├── TicketController                     #   /api/v1/tickets/*
│   │   ├── KnowledgeController                  #   /api/v1/knowledge/*
│   │   └── AdminController                      #   /api/v1/admin/*
│   ├── service/                                 # 业务逻辑层
│   │   ├── agent/                               #   Agent编排
│   │   │   ├── master/                          #     MasterAgent（意图+置信度）
│   │   │   ├── sub/                             #     PolicyAgent / ProcedureAgent / ComplaintAgent
│   │   │   └── scheduler/                       #     SubAgentScheduler
│   │   ├── rag/                                 #   RAG引擎
│   │   │   ├── embedding/                       #     EmbeddingService
│   │   │   ├── retriever/                       #     HybridRetriever
│   │   │   ├── chunker/                         #     DocumentChunker
│   │   │   └── tracer/                          #     SourceTracer
│   │   ├── chat/                                #   对话管理
│   │   │   ├── session/                         #     SessionManager
│   │   │   ├── context/                         #     ContextWindow
│   │   │   └── slot/                            #     SlotFillingEngine + 校验器
│   │   ├── knowledge/                           #   知识库文档管理
│   │   ├── ticket/                              #   工单模块
│   │   ├── audit/                               #   审计日志
│   │   └── security/                            #   安全服务
│   ├── config/                                  # 配置类
│   └── resources/
│       ├── application.yml / application-dev.yml
│       └── db/migration/                        # Flyway迁移SQL
├── frontend/                                    # Vue3 + Element Plus + Pinia
└── docker/                                      # docker-compose.yml + .env
```

> 分层依据：阿里巴巴Java开发手册 — Controller → Service → Mapper，Model 下细分 entity/dto/vo/bo。

---

## 核心业务链路

系统有两条核心对话链路，覆盖全部三个意图：

| 链路 | 意图 | 核心文件 | 特点 |
|------|------|---------|------|
| RAG 对话 | POLICY_CONSULT / PROCEDURE_GUIDE | `ChatService` → `PolicyAgent`/`ProcedureAgent` → `HybridRetriever` | 检索→LLM生成→SSE流式 |
| 工单创建 | COMPLAINT_SUGGEST | `ChatService` → `ComplaintAgent` → `SlotFillingEngine` | FSM+LLM多轮采集→打断→创建 |

### 链路一：RAG 对话（政策咨询 / 办事指引）

```
用户: "行政复议的时效是多久？"
  → ChatService: 意图分类 → POLICY_CONSULT (confidence≥0.6)
  → session → ANSWERING
  → 检索: PolicyAgentTools.search(message)
    → HybridRetriever.search(query, "lak_policy_docs")
      ├─ Dense检索: Qdrant gRPC 相似度搜索 minScore=0.55 → top 10
      ├─ Keyword增强: 中文分词 → Qdrant filter 匹配 → 额外结果
      └─ RRF融合: Reciprocal Rank Fusion (k=60) → top 5 (相似度≥0.75)
    → SourceTracer.buildCitations(fragments)
      → 格式化为: 【sourceNo 第articleNo条】(生效: effectiveDate)\nfragment_text
    → 返回 SearchResult(formattedText, sources)
  → LLM生成: PolicyAgentService.answer(message, formattedDocs)
    → System Prompt: "公安政法领域助手，仅使用提供的检索材料作答"
  → SSE流式: TokenStream → onPartialResponse → SSE message事件 (逐Token)
  → 完成: SSE done事件含 sessionId / intentType / sources
  → session → COMPLIANCE_CHECK → 合规校验(空值/长度/敏感词/溯源检查)
  → 上下文保存
```

**PolicyAgent vs ProcedureAgent**：

| 维度 | PolicyAgent | ProcedureAgent |
|------|-----------|---------------|
| Qdrant Collection | `lak_policy_docs` | `lak_procedure_docs` |
| System Prompt | 公安政法领域助手，引用文档编号和条款号 | 办事指引助手，包含条件、材料、地点、时限、电话 |
| 引用格式 | `【sourceNo 第articleNo条】` | `【docTitle】` |
| SourceTracer | ✅ 有发文字号/条号 | ✅ 有文档标题 |

**SearchResult 模式**（两个 Agent 共用）：
```java
public record SearchResult(String formattedText, List<Map<String, Object>> sources) {}
// formattedText → 注入 LLM Prompt
// sources       → SSE done 事件返回前端 → SourceCitation 组件渲染
```

#### HybridRetriever 检索流水线

```
用户query
  ├─ Dense Retrieval (Qdrant gRPC)
  │   ├─ embeddingService.embed(query) → 1024维向量
  │   ├─ store.search(embedding, topK=10, minScore=DENSE_MIN_SCORE=0.55)
  │   └─ 返回 List<TextSegment> + 相似度分数
  │
  ├─ Keyword Boosting (倒排索引增强)
  │   ├─ 中文分词 → 提取关键词
  │   ├─ 构造 Qdrant filter: payload.keyword IN [kw1, kw2, ...]
  │   └─ 返回额外匹配片段 (topK=10)
  │
  └─ RRF 融合 (Reciprocal Rank Fusion)
      ├─ score_rrf = Σ( 1/(k + rank_i) )  // k=60
      ├─ 对两路结果按 RRF 分数合并排序
      ├─ 最终过滤: similarity ≥ SIMILARITY_THRESHOLD (0.75)
      └─ 返回 top 5 (FUSION_TOP_K)
```

#### RAG 核心组件

| 组件 | 职责 |
|------|------|
| `EmbeddingService` | 调用 text-embedding-v4 生成 1024 维向量 |
| `HybridRetriever` | Dense + Keyword 两路检索 + RRF 融合 |
| `DocumentChunker` | 结构感知分块：政策按"第X条"，办事指南按章节标题（"一、"/"二、"），无结构标记时 fallback 固定大小切分 |
| `SourceTracer` | 构建结构化溯源引用 SourceCitation，生成 toMap() 供前端展示 |
| `DocumentParser` | TXT/PDF/DOCX → 结构化纯文本。PDF: PDFBox → 无文本则 RapidOCR CLI 兜底扫描件。DOCX: Apache POI + Heading 样式 → Markdown |

#### 知识库文档管理流程

```
上传: POST /api/v1/knowledge/documents (multipart)
  → LocalFileStorageService.save() → 文件落盘
  → DocumentParser.parse() → 结构化纯文本
  → DocumentChunker.chunk() → 分块 + 元数据
  → MySQL INSERT (status=DRAFT)
  → 发布(publish): EmbeddingService.embedForStore() → Qdrant add → status=ACTIVE
  → 停用(disable): Qdrant removeAll → status=EXPIRED
  → 删除: DB删除 → Qdrant清理 → 文件清理
```

> **@Qualifier 注意**: `KnowledgeDocumentService` 和 `DocumentExpiryScheduler` 需显式构造函数标注 `@Qualifier("policyEmbeddingStore")` 和 `@Qualifier("procedureEmbeddingStore")`。Lombok `@RequiredArgsConstructor` 可能不复制字段上的 `@Qualifier` 到构造参数，导致 Spring 把 `@Primary` 的 policy 实例注入两个字段，使 PROCEDURE 文档错误写入 `lak_policy_docs`。

---

### 链路二：对话工单创建（投诉建议）

**架构**：FSM管流程 + LLM管理解 — 业界主流混合模式（GlobalDev 2025 / IBM 2024）。

```
用户: "我要投诉"
  → ChatService: 意图分类 → COMPLAINT_SUGGEST
  → ComplaintAgent.process() → SlotFillingEngine.startFilling()
  → transition(COLLECT_INFO) ← 关键状态变更
  → 返回第一个槽位提示: "请问您要反馈的是哪类问题？"

用户后续消息:
  → ChatService: 检测 COLLECT_INFO → 跳过意图分类，直接 handleCollectInfo()
  → SlotFillingEngine.processResponse():
    ├─ Step1: 规则预清洗（去口头前缀 / 手机号正则直提 / 取消提交关键词）
    ├─ Step2: LLM结构化抽取 → JSON: {action, targetSlot, extractedValue, confidence}
    ├─ Step3: JSON Schema校验 → 失败reprompt×2 → 仍失败降级为规则提取
    └─ Step4: 按action分发
        ├─ fill       → 写入槽值，推进下一槽位，更新NL摘要
        ├─ modify     → 回退到指定槽位重新询问
        ├─ chitchat   → 挂起 (interruption:{active:true})，回答闲聊附回钩
        ├─ new_intent → 挂起，ChatService 路由到 PolicyAgent/ProcedureAgent 实际回答
        └─ cancel     → 退出，CLOSED
  → 所有槽位完成 → TicketAdapter.createTicket() → TICKET_SUBMIT

超轮次兜底:
  超5轮 / 用户说"我自己填" / LLM不可用 → redirectToManual
  → extra: {redirectToManual:true, prefilledSlots:{已填数据}}
  → ChatView 显示"去手工填写工单"按钮 → sessionStorage → TicketView 预填表单
```

**5 个槽位定义**：

| 槽位 | 必填 | 校验 | 特殊处理 |
|------|------|------|---------|
| `complaintType` | ✅ | — | normalizeComplaintType: "第3类"/"3"/"第三" → "派出所/民警投诉" |
| `contactName` | ✅ | — | 去口头前缀: "我叫"/"我是"/"我" |
| `contactPhone` | ✅ | `^1[3-9]\d{9}$` | 正则直提优先，无需LLM |
| `description` | ✅ | — | — |
| `attachment` | ❌ | — | 可选，引导回复"无" |

**打断处理**：

```
Redis: interruption:{active, rounds, resumeSlot}
new_intent → 路由到目标Agent（如PolicyAgent RAG检索）→ 答案末尾附回钩
chitchat   → 挂起，返回通用回复 + 回钩
rounds ≥ 3 → 强制回归槽位
slotStage=interrupted → ChatView 显示黄色暂停横幅 + "回到工单填写"按钮
```

**投诉类型规范化**：`normalizeComplaintType()` 支持 16 种变体映射，LLM 路径和规则降级路径都做。

**对话 → 手工表单衔接**：`onResumeSlot()` 只清除前端 interrupted 标记不发假消息；`goToManualTicket()` 通过 sessionStorage 传递已填数据。

#### SlotFillingEngine 关键组件

| 组件 | 职责 |
|------|------|
| `SlotFillingEngine` | FSM管流程（槽位推进、轮次限制、状态转换） |
| `SlotExtractionResult` | LLM结构化输出BO：action / targetSlot / extractedValue / confidence / reasoning |
| `SlotExtractionValidator` | JSON Schema校验 → 格式错/字段缺失/手机号格式错 → reprompt 或降级 |
| `ExtractionParseException` | 校验异常（非业务异常，仅内部控制流，不触发全局异常处理） |
| `extractWithRetry()` | LLM调用 → JSON校验 → 失败重试×2 → 降级 fallbackExtraction() |
| `fallbackExtraction()` | LLM不可用时规则兜底：手机号正则直提、类型映射、关键词检测 |

#### 前端 Slot-Filling UI 状态

| SSE done 中的字段 | ChatView 表现 |
|------------------|-------------|
| `state=COLLECT_INFO, slotStage=fill/modify` | 蓝色 "📋 工单填写中" |
| `state=COLLECT_INFO, slotStage=interrupted` | 黄色 "⏸ 工单填写已暂停" + "回到工单填写"按钮 |
| `state=COLLECT_INFO, slotStage=redirect, redirectToManual=true` | 蓝色 "📝 建议转手工填写" + "去手工填写工单"按钮 |
| `state=TICKET_SUBMIT, ticketNo=TK-xxx` | 绿色 "✅ 工单已创建：TK-xxx" |

前端 SSE 解析需处理 done 事件中的 `extra` / `state` / `slotStage` / `ticketNo` / `redirectToManual` / `prefilledSlots` 字段。

---

## 关键架构决策

### Filter Chain 顺序

| Order | Filter | 职责 |
|-------|--------|------|
| 1 | SensitiveWordPreCheck | 敏感词前置校验，命中直接拒绝 |
| 2 | TraceIdFilter | 生成/提取TraceId，注入MDC |
| 3 | AuditLogInterceptor | 捕获请求体，记录入站日志 |
| 4 | AuthFilter | JWT校验，未认证返回401 |
| 5 | RateLimiter | 限流检查，超限返回429 |

**白名单端点**（不经过AuthFilter）：`GET /api/v1/health`、`POST /api/v1/auth/login`、`GET /api/v1/auth/captcha`

### 主Agent路由

IntentClassifier（LLM意图分类）→ ConfidenceEvaluator（3层置信度：响应校验+硬规则+规则加权，阈值≥0.6）→ RouteDispatcher → SubAgentScheduler

| 意图编码 | 路由目标 | 置信度阈值 |
|----------|---------|-----------|
| POLICY_CONSULT | agent-policy | ≥ 0.6 |
| PROCEDURE_GUIDE | agent-procedure | ≥ 0.6 |
| COMPLAINT_SUGGEST | agent-complaint | ≥ 0.6 |
| CHITCHAT / UNKNOWN | 兜底处理 | < 0.6 |

硬规则：投诉关键词命中（"我要投诉"/"我要举报"/"不作为"）→ 置信度 ≥ 0.85，意图锁定 COMPLAINT_SUGGEST。发文字号命中（"X政发〔20XX〕XX号"）→ POLICY_CONSULT。

### 会话状态机

`NEW` → `INTENT_CHECK` → `ANSWERING` | `COLLECT_INFO` | `FALLBACK` → `COMPLIANCE_CHECK` | `TICKET_SUBMIT` → `CLOSED`

| 状态 | 触发 | 关键行为 |
|------|------|---------|
| NEW | 会话创建 | 分配UUID sessionId，Redis Hash + MySQL 双写 |
| INTENT_CHECK | 首条消息到达 | 调用 MasterAgent.route() |
| ANSWERING | 置信度≥0.6（政策/指引） | RAG检索 + LLM生成 + SSE流式 |
| COLLECT_INFO | COMPLAINT_SUGGEST | 短路路由跳过意图分类，SlotFillingEngine FSM+LLM管道处理，支持打断挂起 |
| FALLBACK | 置信度<0.5 | 兜底模板，禁止调大模型 |
| COMPLIANCE_CHECK | 答复生成完毕 | 合规校验（COLLECT_INFO时跳过） |
| TICKET_SUBMIT | 槽位全部填完 | TicketAdapter.createTicket() |
| CLOSED | 关闭/超时/取消 | Redis TTL 1800s |

---

## 编码规范

遵循《阿里巴巴Java开发手册（泰山版）》。以下结合 LAK-Agent 特点摘录关键约束。

### 命名与分层

| 模型 | 后缀 | 职责 | 位置 |
|------|------|------|------|
| DO (Data Object) | `Xxx` | 与数据库表一一对应 | Mapper → Service 内部 |
| DTO (Data Transfer) | `XxxDTO` | Controller 入参 / Service 间传输 | Controller → Service |
| VO (View Object) | `XxxVO` | 接口响应 | Controller → 前端 |
| BO (Business Object) | `XxxBO` | 业务逻辑中间结果 | Service 内部 |

> **禁止**：VO 暴露 DO、Controller 返回 DO。Boolean 字段不加 `is` 前缀。DO 不用 Lombok `@Data`（集合关联可能循环引用）→ 用 `@Getter @Setter @ToString`。

### 异常体系

```
BusinessException (code + message)
  ├── AuthException       LAK-01-xxx
  ├── ChatException       LAK-02-xxx
  ├── TicketException     LAK-03-xxx
  ├── SensitiveWordException
  ├── RateLimitException
  └── ModelException
```

> 不在循环内 try-catch；异常信息必须包含现场关键参数；日志用 SLF4J 占位符，禁止字符串拼接和 `System.out.println()`。

### 项目硬约束

- **审计日志**：全量落库，按月分表（`audit_log_yyyyMM`），禁止物理删除（仅 INSERT+SELECT），留存6个月
- **AI答复溯源**：每条必须携带 docId + sourceNo + effectiveDate，禁止无依据输出
- **敏感词双向校验**：前置 Filter 拦截输入 + 后置 Validator 校验输出，支持 POST `/admin/sensitive-words/reload` 热加载
- **熔断超时**：Resilience4j CircuitBreaker，大模型 5次失败/30s窗口 → 降级
- **会话管理**：Redis Hash `session:{sessionId}`，TTL 1800s，禁止本地内存
- **低置信度兜底**：confidence < 0.5 → fallback 兜底模板，禁止 AI 作答。0.5-0.7 区间正常路由（needsVoting 预留）
- **统一响应**：`{code, message, data, traceId}`
- **上下文窗口**：最近10轮（20条消息），Token ≤ 6000
- **消息长度**：≤ 2000 字符

---

## 端口与端点速查

| 端口 | 用途 |
|------|------|
| 8080 | 业务REST API |
| 8081 | Actuator 管理/监控 |

API前缀：`/api/v1/`

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/health` | 无 | 健康检查 |
| POST | `/auth/login` | 无 | 用户登录 |
| GET | `/auth/captcha` | 无 | 获取验证码 |
| POST | `/auth/refresh` | 无 | 刷新Token |
| POST | `/chat/message` | JWT | 发送消息（JSON / SSE流式） |
| GET | `/chat/sessions` | JWT | 会话列表（分页） |
| GET | `/chat/sessions/{sessionId}` | JWT | 会话历史详情 |
| DELETE | `/chat/sessions/{sessionId}` | JWT | 删除会话（软删除） |
| POST | `/knowledge/documents` | JWT | 上传知识文档 |
| GET | `/knowledge/documents` | JWT | 文档列表 |
| POST | `/knowledge/documents/{docId}/reindex` | JWT | 重新索引 |
| POST | `/tickets` | JWT | 创建工单 |
| GET | `/tickets/mine` | JWT | 我的工单 |
| GET | `/tickets/{ticketNo}` | JWT | 工单详情 |
| POST | `/admin/sensitive-words/reload` | JWT+ADMIN | 敏感词热加载 |

---

## 配置项速查

### 关键环境变量

| 变量 | 说明 |
|------|------|
| `DASHSCOPE_API_KEY` | 百炼大模型API密钥 |
| `JWT_SECRET` | JWT签名密钥 |
| `QDRANT_HOST` | Qdrant主机地址 |
| `REDIS_HOST` | Redis主机地址 |
| `MYSQL_URL` | MySQL JDBC URL |
| `MINIO_ENDPOINT` | MinIO服务地址（生产） |

### Redis Key 命名规范

| Key Pattern | 用途 | TTL | DB |
|-------------|------|-----|----|
| `session:{sessionId}` | 会话状态Hash | 1800s | 0 |
| ↳ `slotValues` | 槽位值 JSON Map | 1800s | 0 |
| ↳ `currentSlot` | 当前槽位索引 | 1800s | 0 |
| ↳ `fillRound` | 已填充轮次 | 1800s | 0 |
| ↳ `nlSummary` | 自然语言摘要（NL-DST） | 1800s | 0 |
| ↳ `interruption` | 打断状态 `{active, rounds, resumeSlot}` | 1800s | 0 |
| `rate_limit:{userId}:{apiPath}` | 限流计数器 | 60s | 1 |
| `captcha:{captchaKey}` | 验证码缓存 | 300s | 0 |
| `lock:knowledge_update` | 知识库更新分布式锁 | 300s | 0 |

### RAG 关键参数

| 参数 | 值 | 常量 |
|------|-----|------|
| 粗排Top-K | 10 | `DENSE_TOP_K` |
| 精排Top-K | 5 | `FUSION_TOP_K` |
| 密集检索最小分数 | 0.55 | `DENSE_MIN_SCORE` |
| 精排相似度阈值 | ≥ 0.75 | `SIMILARITY_THRESHOLD` |
| RRF 融合常数 | 60 | `RRF_K` |
| 检索超时 | 3s | `RETRIEVAL_TIMEOUT_SECONDS` |
| Embedding 维度 | 1024 | `EMBEDDING_DIMENSION` |

### Qdrant 连接

| 参数 | 值 |
|------|-----|
| gRPC Port | 6334 |
| REST Port | 6333 |
| 连接超时 | 10s |
| 索引类型 | HNSW（M=16, ef_construct=100） |

### Token & 认证有效期

| 项 | 值 |
|----|-----|
| Access Token | 2小时 |
| Refresh Token | 7天 |
| 验证码TTL | 5分钟 |

---

## 常见错误码速查

| errorCode | HTTP | 含义 |
|-----------|------|------|
| LAK-01-001 | 401 | 未认证 |
| LAK-01-002 | 401 | 用户名或密码错误 |
| LAK-01-003 | 403 | 账户已禁用 |
| LAK-01-004 | 400 | 验证码错误 |
| LAK-01-005 | 400 | 验证码已过期 |
| LAK-01-006 | 401 | Access Token 已过期 |
| LAK-01-007 | 401 | Refresh Token 无效 |
| LAK-02-101 | 403 | 消息命中敏感词 |
| LAK-02-102 | 429 | 请求频率过高 |
| LAK-02-103 | 504 | 大模型调用超时 |
| LAK-02-104 | 404 | 会话不存在 |
| LAK-03-201 | 500 | 工单创建失败 |
| LAK-03-202 | 404 | 工单不存在 |
| LAK-98-601 | 400 | 参数校验失败 |
| LAK-99-501 | 503 | 大模型不可用（降级） |
| LAK-99-502 | 500 | 系统内部错误 |

---

## 文档索引

| 文件名 | 说明 |
|--------|------|
| `政法智能知识Agent平台.md` | 项目定位、技术栈、业务流程、编码总纲 |
| `docs/design/系统架构设计说明书.md` | 四层架构、Filter Chain、会话状态机、Agent路由 |
| `docs/design/数据库设计说明书.md` | ER图、6张核心表DDL、索引策略 |
| `docs/design/接口设计说明书.md` | 11个API端点、统一响应、错误码体系 |
| `docs/design/多轮对话与Agent调度设计.md` | 意图路由、子Agent调度、SlotFilling引擎 |
| `docs/design/对话工单槽位填充设计.md` | FSM+LLM混合架构、打断处理、知识蒸馏方案 |
| `docs/design/知识库与RAG详细设计.md` | Hybrid检索、文档分块、溯源追踪 |
| `docs/design/安全合规设计说明书.md` | 敏感词、审计日志、数据脱敏 |
| `docs/design/部署运维手册.md` | Docker-Compose、环境变量、备份恢复 |
