# Task 1 完成报告：系统架构设计说明书

## 1. 状态

**DONE** -- 文档已完成全部 7 个产出章节 + 附录，通过自审。

## 2. 产出文件列表

| 文件 | 路径 | 说明 |
|------|------|------|
| 系统架构设计说明书 | `docs/design/系统架构设计说明书.md` | 主产出文档，约 1200 行，7 章节 + 3 附录 |

## 3. 自审发现

### 3.1 可能不够清晰的部分

1. **敏感词库的具体词表内容未定义**：文档描述了双向校验的拦截点和机制，但未给出具体的敏感词列表样例。这是刻意的——敏感词库应根据部署单位的实际需求配置，属于运维配置而非架构设计范畴。建议 Task 6（安全合规设计说明书）中补充词库分类框架。

2. **百炼 API Key 的具体加密存储方案未展开**：文档在多处提到"Vault/KMS"但未指定具体产品和配置。这是因为密钥管理方案依赖于政法单位已有的基础设施。已在附录 C 列入待决策项。

3. **工单系统的接口协议未定**：内部工单系统的接口形式（REST/SOAP/MQ）在架构设计阶段无法确定，`TicketAdapter` 接口定义的是抽象契约，具体实现需要在对接时完成。已在附录 C 列入待决策项。

4. **仅做了串行 Agent 调度，并行调度仅预留扩展点**：审查报告提到"是否可能出现跨 Agent 场景"，本设计明确当前为单一意图场景，多 Agent 并行调度通过 `RoutingDecision` 的多 target 字段预留，但未给出详细的并行调度时序图。这是一个刻意的范围控制——并行调度属于 P1 优化项。

### 3.2 需要后续任务注意的决策

1. **置信度阈值 0.6 需要实际数据校准**：当前 0.6 是基于行业经验的初始值，实际部署后应通过 A/B 测试或人工标注数据校准。建议 Task 2（数据库设计）中预留一个 `confidence_threshold_config` 配置表。

2. **会话 TTL 30 分钟可能不适应所有场景**：投诉场景的多轮对话跨度可能超过 30 分钟。当前设计写的是 30 分钟，SLot-Filling 每轮会刷新 TTL。如实际场景有变化，30 分钟的可配置化应在 `application.yml` 中提供 `lak.session.ttl` 属性。

3. **Mermaid 图中的 `rect rgb(...)` 着色语法**：GitHub/GitLab 原生 Mermaid 渲染器均支持该语法，但某些轻量级 Markdown 预览工具可能不渲染色彩。时序图的核心逻辑（箭头、分支、标注）在任何渲染器中均可正确显示。

4. **LangChain4j 1.14.0 + Spring Boot 3.4.2 兼容性**：架构设计中已标记此风险。建议在第一个 Sprint 的 POC 阶段（编码前）先验证两个依赖的自动配置是否冲突。

## 4. 对后续任务的接口承诺

以下是在本文档中定义的、后续任务（Task 2 数据库设计、Task 3 接口设计）必须遵守的实体名、端口号、配置项名称。

### 4.1 数据库相关承诺（供 Task 2 使用）

| 承诺项 | 取值 | 来源章节 |
|-------|------|---------|
| 数据库名 | `lak_ai_platform` | 1.3 全局约束 |
| 核心业务表 | `chat_session`, `chat_message`, `ticket`, `audit_log`, `knowledge_document` | 2.5.1 |
| `audit_log` 分表策略 | 按 `yyyyMM` 分表：`audit_log_202601` | 2.5.1 / 6.3 |
| 会话 Redis Key | `session:{sessionId}` (Hash, TTL 1800s) | 2.4.2 |
| 限流 Redis Key | `rate_limit:{userId}:{apiPath}` (TTL 60s) | 2.5.3 |
| 分布式锁 Redis Key | `lock:knowledge_update` (TTL 300s) | 2.5.3 |
| Redis DB 分配 | db0（会话）/ db1（限流） | 2.5.3 |

### 4.2 Qdrant 相关承诺（供 Task 2/4 使用）

| 承诺项 | 取值 | 来源章节 |
|-------|------|---------|
| Collection 名 | `lak_policy_docs` (AgentA), `lak_procedure_docs` (AgentB) | 2.4.1 / 2.5.2 |
| 向量维度 | 1536 | 2.4.1 |
| 距离度量 | Cosine | 2.5.2 |
| 相似度阈值 | ≥ 0.75 | 2.4.1 |
| Top-K 粗排 | 10 | 2.4.1 |
| Top-K 精排 | 5 | 2.4.1 |

### 4.3 API 与端口承诺（供 Task 3 使用）

| 承诺项 | 取值 | 来源章节 |
|-------|------|---------|
| 业务端口 | `8080` | 1.3 全局约束 |
| 管理端口 | `8081` | 1.3 全局约束 |
| API 前缀 | `/api/v1/` | 2.2 |
| 认证端点 | `POST /api/v1/auth/login` | 2.2 / 6.2 |
| 验证码端点 | `GET /api/v1/auth/captcha` | 2.2 |
| 健康检查端点 | `GET /api/v1/health` | 2.2 |
| 发送消息端点 | `POST /api/v1/chat/message` | 3.1 |
| 敏感词热加载端点 | `POST /api/v1/admin/sensitive-words/reload` | 6.4 |
| JWT Header | `Authorization: Bearer <token>` | 6.2 |
| JWT 签发者 | `lak-ai-platform` | 6.2 |
| Access Token 有效期 | 2 小时 | 6.2 |
| Refresh Token 有效期 | 7 天 | 6.2 |

### 4.4 Agent 架构承诺（供 Task 3/5 使用）

| 承诺项 | 取值 | 来源章节 |
|-------|------|---------|
| 子Agent ID | `agent-policy`, `agent-procedure`, `agent-complaint` | 2.3.2 |
| 意图枚举值 | `POLICY_CONSULT`, `PROCEDURE_GUIDE`, `COMPLAINT_SUGGEST`, `CHITCHAT`, `UNKNOWN` | 2.3.1 |
| 置信度阈值 | 0.6 | 2.3.1 |
| 会话状态机状态 | NEW, INTENT_CHECK, ANSWERING, COLLECT_INFO, FALLBACK, COMPLIANCE_CHECK, TICKET_SUBMIT, CLOSED | 2.4.2 |
| Slot-Filling 最大轮数 | 5 | 2.4.2 |
| 上下文窗口 | 最近 10 轮（20 条消息），Token 上限 6000 | 2.4.2 |

### 4.5 配置项命名承诺（供所有 Task 使用）

| 配置项 | 环境变量 | 建议值 | 来源章节 |
|-------|---------|--------|---------|
| 服务端口 | `SERVER_PORT` | 8080 | 4.1 |
| 管理端口 | `MANAGEMENT_PORT` | 8081 | 4.1 |
| MySQL 连接 | `MYSQL_HOST/PORT/DATABASE/USER/PASSWORD` | — | 4.1 |
| Redis 连接 | `REDIS_HOST/PORT/PASSWORD` | — | 4.1 |
| Qdrant 连接 | `QDRANT_HOST/QDRANT_GRPC_PORT/QDRANT_REST_PORT` | grpc=6334, rest=6333 | 4.1 |
| 百炼 API | `DASHSCOPE_API_KEY/DASHSCOPE_MODEL/DASHSCOPE_EMBEDDING_MODEL` | qwen3.7-max / text-embedding-v4 | 4.1 |
| JWT | `JWT_SECRET/JWT_EXPIRATION` | — | 4.1 |
| 日志 | `LOG_LEVEL/LOG_RETENTION_DAYS` | INFO / 180 | 4.1 |

## 5. 与后续任务的工作流建议

Task 2（数据库设计）和 Task 3（接口设计）可并行启动，因为它们对 Task 1 的依赖是只读的（读取上述承诺项），彼此之间没有写完才能读的强依赖。但实际上 Task 3 的 Request/Response Schema 会引用 Task 2 的表字段定义，因此建议 Task 2 先完成核心表 DDL，Task 3 再开始。

---

*报告生成时间：2026-06-17*
*Task 1 执行者：AI 架构助手*
