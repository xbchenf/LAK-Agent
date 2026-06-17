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
├── CLAUDE.md                                  # 本文件
├── 政法智能知识Agent平台.md                     # 项目总纲
├── pom.xml
├── docs/
│   └── design/
│       ├── 系统架构设计说明书.md                  # 架构分层 + Filter Chain + 状态机
│       ├── 数据库设计说明书.md                    # 6个核心表DDL
│       └── 接口设计说明书.md                      # API Spec + 错误码
├── src/main/java/com/lak/ai/
│   ├── LakAiApplication.java
│   ├── agent/                                  # Agent编排
│   │   ├── master/                             #   MasterAgent
│   │   ├── sub/                                #   子Agent实现
│   │   └── scheduler/                          #   SubAgentScheduler
│   ├── rag/                                    # RAG检索引擎
│   │   ├── embedding/
│   │   ├── retriever/
│   │   ├── reranker/
│   │   └── tracer/                             #   SourceTracer
│   ├── chat/                                   # 对话管理
│   │   ├── session/                            #   会话状态机
│   │   ├── context/                            #   上下文窗口
│   │   └── slot/                               #   Slot-Filling
│   ├── ticket/                                 # 工单模块
│   └── common/                                 # 公共组件
│       ├── response/                           #   ApiResponse
│       ├── exception/                          #   全局异常
│       └── constant/                           #   常量/枚举
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   └── db/migration/                           # Flyway迁移脚本
└── docker/
    └── docker-compose.yml
```

## 编码规范与硬约束

- **审计日志：** 全量落库（入参/出参/模型调用/检索片段），按月分表（`audit_log_YYYYMM`），禁止物理删除，留存6个月
- **AI答复溯源：** 每条AI答复必须携带文档来源（docId）+ 文件编号（title）+ 生效时间（effectiveDate），禁止无依据输出
- **敏感词双向校验：** 前置Filter拦截用户输入，后置Validator校验AI答复内容；词库文件 `/config/sensitive-words.txt`，支持动态加载
- **熔断超时：** 大模型/第三方接口必须配置 Resilience4j CircuitBreaker + 超时控制；大模型熔断阈值：5次失败/30s窗口
- **会话管理：** Redis持久化（`session:{sessionId}` Hash），TTL 1800s（30分钟），无本地内存会话
- **低置信度兜底：** 置信度 < 0.6 禁止AI自动作答，返回人工客服引导
- **统一响应格式：** `{ "code": 200, "message": "success", "data": {...}, "traceId": "..." }`
- **错误码规范：** `LAK-<模块>-<编号>`，模块含 AUTH/CHAT/TICKET/SYSTEM/VALIDATION
- **命名规范：** 包名 `com.lak.ai`，服务名 `lak-ai-platform`，数据库 `lak_ai_platform`
- **上下文窗口：** 保留最近10轮对话（20条消息），送入大模型的Token上限 ≤ 6000
- **消息长度限制：** 单条消息 ≤ 2000 字符

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
| LAK-AUTH-001 | 401 | 未认证 |
| LAK-AUTH-004 | 400 | 验证码错误 |
| LAK-AUTH-006~008 | 401 | Refresh Token异常 |
| LAK-CHAT-101 | 403 | 消息命中敏感词 |
| LAK-CHAT-102 | 429 | 请求频率过高 |
| LAK-CHAT-103 | 504 | 大模型调用超时 |
| LAK-CHAT-104 | 404 | 会话不存在 |
| LAK-SYSTEM-501 | 503 | 大模型不可用（降级） |
| LAK-VALIDATION-601 | 400 | 参数校验失败 |

## 文档索引

| 文件名 | 路径 | 说明 |
|--------|------|------|
| 项目总纲 | `政法智能知识Agent平台.md` | 项目定位、技术栈、业务流程、编码总纲 |
| 架构设计说明书 | `docs/design/系统架构设计说明书.md` | 四层架构、Filter Chain、会话状态机、Agent路由 |
| 数据库设计说明书 | `docs/design/数据库设计说明书.md` | ER图、6张核心表DDL、索引策略 |
| 接口设计说明书 | `docs/design/接口设计说明书.md` | 11个API端点、统一响应、错误码体系（25个码） |
