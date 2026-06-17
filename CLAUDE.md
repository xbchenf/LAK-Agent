# LAK-Agent 政法智能知识Agent平台

> Legal Affairs Knowledge Agent Platform — 面向政法行业私有化部署的智能问答平台。

## 项目概览

政法行业私有化智能问答平台，采用主Agent+多子Agent架构。统一前置拦截 -> 主Agent意图识别+置信度判断 -> 路由分发至对应子Agent -> 业务处理 -> 结果合规校验 -> 统一响应封装 + 全链路审计日志落库。低置信度场景（<0.6）自动兜底至人工客服，禁止AI自动作答。

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
| 向量模型 | text-embedding-v4（百炼） | 1536维，文本向量化 |

**包名**: `com.lak.ai` | **服务名**: `lak-ai-platform` | **数据库名**: `lak_ai_platform`

## 项目结构

```
lak-ai-platform/
├── CLAUDE.md
├── 政法智能知识Agent平台.md                     # 项目总纲
├── pom.xml
├── docs/
│   └── design/
│       ├── 系统架构设计说明书.md
│       ├── 数据库设计说明书.md
│       └── 接口设计说明书.md
├── src/main/java/com/lak/ai/
│   ├── LakAiApplication.java
│   ├── controller/                              # 控制器层（接口暴露）
│   │   ├── AuthController                       #   /api/v1/auth/*
│   │   ├── ChatController                       #   /api/v1/chat/*
│   │   ├── TicketController                     #   /api/v1/tickets/*
│   │   └── AdminController                      #   /api/v1/admin/*
│   ├── service/                                 # 业务逻辑层
│   │   ├── agent/                               #   Agent编排
│   │   │   ├── master/                          #     MasterAgent（意图+置信度）
│   │   │   ├── sub/                             #     子Agent（PolicyAgent等）
│   │   │   └── scheduler/                       #     SubAgentScheduler
│   │   ├── rag/                                 #   RAG引擎
│   │   │   ├── embedding/                       #     EmbeddingService
│   │   │   ├── retriever/                       #     HybridRetriever
│   │   │   ├── chunker/                         #     DocumentChunker
│   │   │   └── tracer/                          #     SourceTracer
│   │   ├── chat/                                #   对话管理
│   │   │   ├── session/                         #     SessionManager
│   │   │   ├── context/                         #     ContextWindow
│   │   │   └── slot/                            #     SlotFillingEngine
│   │   ├── ticket/                              #   工单模块
│   │   ├── audit/                               #   审计日志
│   │   └── security/                            #   安全服务
│   ├── mapper/                                  # Mybatis-Plus Mapper
│   ├── model/                                   # 数据模型（按子包分类）
│   │   ├── entity/                              #   数据库实体（DO）
│   │   ├── dto/                                 #   数据传输对象
│   │   ├── vo/                                  #   视图对象（响应VO）
│   │   └── bo/                                  #   业务对象
│   ├── enums/                                   # 枚举类
│   ├── constant/                                # 常量类
│   ├── exception/                               # 自定义异常
│   ├── common/                                  # 公共组件
│   │   ├── response/                            #   ApiResponse / PageResult
│   │   └── context/                             #   RequestContext / TraceId
│   └── config/                                  # 配置类
│       ├── security/                            #   Security / Filter 配置
│       └── Resilience4jConfig                   #   熔断配置
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── db/migration/                            # Flyway迁移SQL
│   └── config/
│       ├── prompts/                             # Agent Prompt 模板
│       └── sensitive-words.txt                  # 敏感词库
└── docker/
    └── docker-compose.yml
```

> 分层依据：阿里巴巴Java开发手册 — 分层领域模型规约：Controller → Service → Mapper，Model 下细分 entity/dto/vo/bo。

## 编码规范 — 阿里巴巴Java开发手册

本工程遵循《阿里巴巴Java开发手册（泰山版）》的核心规约。以下结合 LAK-Agent 项目特点摘录关键约束。

### 命名规范

| 元素 | 规范 | 示例 |
|------|------|------|
| 包名 | 全小写，点分隔 | `com.lak.ai.service.rag` |
| 类名 | UpperCamelCase | `PolicyAgent`, `HybridRetriever` |
| 方法名 | lowerCamelCase | `classifyIntent()`, `searchDocuments()` |
| 常量 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT`, `DEFAULT_TTL_SECONDS` |
| 变量 | lowerCamelCase | `sessionId`, `confidenceThreshold` |
| Controller | `XxxController` | `ChatController`, `AuthController` |
| Service 接口 | `XxxService` | `ChatService`, `RagService` |
| Service 实现 | `XxxServiceImpl` | `ChatServiceImpl` |
| Mapper | `XxxMapper` | `ChatSessionMapper` |
| Entity (DO) | `Xxx`（表名转UpperCamelCase） | `ChatSession`, `AuditLog` |
| DTO | `XxxDTO` | `ChatMessageDTO`, `LoginDTO` |
| VO | `XxxVO` | `ChatSessionVO`, `MessageVO` |
| BO | `XxxBO` | `RoutingDecisionBO`, `ConfidenceBO` |
| 枚举类 | `XxxEnum`（或直接描述性名称） | `IntentTypeEnum`, `SessionStatus` |

### 分层领域模型（不得混用）

| 模型 | 后缀 | 职责 | 使用位置 |
|------|------|------|---------|
| **DO** (Data Object) | `Xxx` | 与数据库表一一对应 | Mapper → Service 内部 |
| **DTO** (Data Transfer) | `XxxDTO` | Controller 入参 / Service 间传输 | Controller → Service |
| **VO** (View Object) | `XxxVO` | 接口响应，面向前端展示 | Controller → 前端 |
| **BO** (Business Object) | `XxxBO` | 封装业务逻辑中间结果 | Service 内部 |
| **Query** | `XxxQuery` | 分页/列表查询参数封装 | Controller 入参 |

> **禁止**：VO 直接暴露 DO、Controller 直接返回 DO、Service 直接接收 HttpServletRequest/Response。

### POJO 规约

- **Boolean 字段不加 `is` 前缀**：数据库字段 `is_deleted` → Java 属性 `deleted`（Mybatis-Plus 自动映射）
- **序列化**：DO/VO/DTO 如需序列化，实现 `Serializable` 接口并声明 `serialVersionUID`
- **禁止 Lombok `@Data` 用在 DO 上**（集合类关联可能触发循环引用）→ 使用 `@Getter @Setter @ToString`

### 异常处理

```
RuntimeException
  └── BusinessException               # 业务异常基类（code + message）
        ├── AuthException             #   认证异常（LAK-AUTH-xxx）
        ├── ChatException             #   对话异常（LAK-CHAT-xxx）
        ├── TicketException           #   工单异常（LAK-TICKET-xxx）
        ├── SensitiveWordException    #   敏感词拦截（不返回详情）
        ├── RateLimitException        #   限流（返回429）
        └── ModelException            #   大模型异常（熔断/超时/不可用）
```

> **阿里巴巴规约**：不在业务代码中使用 `catch (Exception e)` 吞掉异常；不在循环内 try-catch；异常信息必须包含现场关键参数。

### 日志规约（SLF4J + Logback）

- **日志级别**：开发 `DEBUG`，生产 `INFO`（关键节点 `WARN`/`ERROR`）
- **占位符**：`log.info("意图分类完成, sessionId={}, intent={}, cost={}ms", sessionId, intent, cost)` — 禁止字符串拼接
- **禁止** `System.out.println()`、`e.printStackTrace()`
- **审计日志**：使用 `@AuditLog` 自定义注解 + AOP 切面，统一写入 `audit_log` 表
- **TraceId**：所有日志自动带 TraceId（Filter 注入 MDC 后 `%X{traceId}`）

### 常量定义

- 跨模块公共常量 → `com.lak.ai.constant.CommonConstants`
- 模块内常量 → 各自 `XxxConstants`（如 `AgentConstants`、`ChatConstants`）
- 禁止在代码中直接写魔法值（意图编码、状态值、阈值、超时时间均定义为常量）

### Mybatis-Plus 规约

- **BaseEntity**：抽取 `id`, `createTime`, `updateTime` 为公共基类，DO 继承
- **自动填充**：`createTime` / `updateTime` 使用 Mybatis-Plus `MetaObjectHandler` 自动填充，禁止手动 `new Date()`
- **逻辑删除**：需要软删除的表使用 `@TableLogic` 注解，`deleted = 0/1`
- **分表**：审计日志按 `yyyyMM` 分表，使用 Mybatis-Plus 动态表名拦截器 `DynamicTableNameInnerInterceptor`

## 编码规范 — 项目硬约束

以下约束为 LAK-Agent 政务场景特有的合规要求，优先级高于通用规约：

- **审计日志：** 全量落库（入参/出参/模型调用/检索片段），按月分表（`audit_log_yyyyMM`），禁止物理删除（仅 INSERT+SELECT 权限），留存6个月
- **AI答复溯源：** 每条AI答复必须携带文档来源（docId）+ 文件编号（sourceNo）+ 生效时间（effectiveDate），禁止无依据输出
- **敏感词双向校验：** 前置 Filter 拦截用户输入，后置 Validator 校验 AI 答复；词库文件 `/config/sensitive-words.txt`，支持 `POST /api/v1/admin/sensitive-words/reload` 热加载
- **熔断超时：** 大模型/第三方接口必须配置 Resilience4j CircuitBreaker + @Retry + @Timeout；大模型熔断阈值：5次失败/30s窗口 → 触发降级
- **会话管理：** Redis Hash 持久化（`session:{sessionId}`），TTL 1800s，禁止本地内存会话
- **低置信度兜底：** 置信度 < 0.6 禁止 AI 自动作答，返回人工客服引导模板
- **统一响应格式：** `{ "code": 200, "message": "success", "data": {...}, "traceId": "..." }`
- **错误码规范：** 5位数字 `LAK-XX-XXX`，模块 2位 + 序号 3位（AUTH=01, CHAT=02, TICKET=03, SYSTEM=99, VALIDATION=98）
- **命名前缀：** 包名 `com.lak.ai`，服务名 `lak-ai-platform`，数据库 `lak_ai_platform`，API 前缀 `/api/v1/`
- **上下文窗口：** 保留最近10轮对话（20条消息），送入大模型的 Token 上限 ≤ 6000
- **消息长度：** 单条消息 ≤ 2000 字符

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

### 主Agent路由机制

IntentClassifier（意图识别）→ ConfidenceEvaluator（置信度判断，阈值≥0.6）→ RouteDispatcher → SubAgentScheduler

| 意图编码 | 路由目标 | 置信度阈值 |
|----------|---------|-----------|
| POLICY_CONSULT | agent-policy（政策咨询） | ≥ 0.6 |
| PROCEDURE_GUIDE | agent-procedure（办事指引） | ≥ 0.6 |
| COMPLAINT_SUGGEST | agent-complaint（投诉建议） | ≥ 0.6 |
| CHITCHAT / UNKNOWN | 兜底处理 | < 0.6 |

### 子Agent ID

- `agent-policy` — 政策咨询Agent，使用 `lak_policy_docs` Collection
- `agent-procedure` — 办事指引Agent，使用 `lak_procedure_docs` Collection
- `agent-complaint` — 投诉建议Agent，多轮Slot-Filling + 工单创建

### 会话状态机（8个状态）

`NEW` → `INTENT_CHECK` → `ANSWERING` | `COLLECT_INFO` | `FALLBACK` → `COMPLIANCE_CHECK` | `TICKET_SUBMIT` → `CLOSED`

| 状态 | 触发 |
|------|------|
| NEW | 会话创建，分配sessionId |
| INTENT_CHECK | 首条消息到达，调用主Agent |
| ANSWERING | 置信度≥0.6（政策/指引），RAG+LLM |
| COLLECT_INFO | 投诉意图，Slot-Filling（最多5轮） |
| FALLBACK | 置信度<0.6，人工兜底 |
| COMPLIANCE_CHECK | 答复生成完毕，合规校验 |
| TICKET_SUBMIT | 投诉信息补齐，调用工单接口 |
| CLOSED | 用户关闭/会话超时 |

### RAG检索参数

| 参数 | 值 |
|------|-----|
| 粗排Top-K | 10 |
| 精排Top-K | 5 |
| 相似度阈值 | ≥ 0.75 |
| 最大检索超时 | 3s |
| Embedding维度 | 1536 |
| Qdrant Collection | `lak_policy_docs` / `lak_procedure_docs` |

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
| POST | `/chat/message` | JWT | 发送消息（支持SSE流式） |
| GET | `/chat/sessions` | JWT | 会话列表（分页） |
| GET | `/chat/sessions/{sessionId}` | JWT | 会话历史详情 |
| DELETE | `/chat/sessions/{sessionId}` | JWT | 删除会话（软删除） |
| POST | `/tickets` | JWT | 创建工单 |
| GET | `/tickets/{ticketNo}` | JWT | 查询工单详情 |
| POST | `/admin/sensitive-words/reload` | JWT+ADMIN | 敏感词热加载 |

## 配置项速查

### 关键环境变量

| 变量 | 说明 |
|------|------|
| `DASHSCOPE_API_KEY` | 百炼大模型API密钥 |
| `JWT_SECRET` | JWT签名密钥 |
| `QDRANT_HOST` | Qdrant主机地址（dev: `localhost` / prod: `qdrant.internal`） |
| `REDIS_HOST` | Redis主机地址 |
| `MYSQL_URL` | MySQL JDBC URL |
| `MINIO_ENDPOINT` | MinIO服务地址（生产） |

### Redis Key 命名规范

| Key Pattern | 用途 | TTL | DB |
|-------------|------|-----|----|
| `session:{sessionId}` | 会话状态Hash | 1800s | 0 |
| `rate_limit:{userId}:{apiPath}` | 限流计数器 | 60s | 1 |
| `captcha:{captchaKey}` | 验证码缓存 | 300s | 0 |
| `lock:knowledge_update` | 知识库更新分布式锁 | 300s | 0 |

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
| LAK-01-008 | 401 | Token 已被废弃 |
| LAK-02-101 | 403 | 消息命中敏感词 |
| LAK-02-102 | 429 | 请求频率过高 |
| LAK-02-103 | 504 | 大模型调用超时 |
| LAK-02-104 | 404 | 会话不存在 |
| LAK-03-201 | 500 | 工单创建失败 |
| LAK-03-202 | 404 | 工单不存在 |
| LAK-98-601 | 400 | 参数校验失败 |
| LAK-99-501 | 503 | 大模型不可用（降级） |
| LAK-99-502 | 500 | 系统内部错误 |

## 文档索引

| 文件名 | 路径 | 说明 |
|--------|------|------|
| 项目总纲 | `政法智能知识Agent平台.md` | 项目定位、技术栈、业务流程、编码总纲 |
| 架构设计说明书 | `docs/design/系统架构设计说明书.md` | 四层架构、Filter Chain、会话状态机、Agent路由 |
| 数据库设计说明书 | `docs/design/数据库设计说明书.md` | ER图、6张核心表DDL、索引策略 |
| 接口设计说明书 | `docs/design/接口设计说明书.md` | 11个API端点、统一响应、错误码体系（25个码） |
