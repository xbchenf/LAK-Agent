# Task 3 完成报告：接口设计说明书（API Spec）

## 1. 状态

**DONE** -- 文档已完成全部 8 个产出章节 + OpenAPI 3.0 附录，通过自审。

## 2. 产出文件列表

| 文件 | 路径 | 说明 |
|------|------|------|
| 接口设计说明书 | `docs/design/接口设计说明书.md` | 主产出文档，约 1400 行，8 章节 + 1 附录 |

## 3. 自审发现

### 3.1 与 Task 1 的命名一致性检查

| 检查项 | Task 1 承诺 | Task 3 产出 | 结论 |
|--------|------------|------------|------|
| API 前缀 | `/api/v1/` | 全部端点使用 `/api/v1/` 前缀 | 一致 |
| 5 个强制端点 | health, auth/login, auth/captcha, chat/message, admin/sensitive-words/reload | 全部覆盖 | 一致 |
| JWT Header | `Authorization: Bearer <token>` | 全部接口标注 JWT 认证要求 | 一致 |
| Token 有效期 | Access 2h / Refresh 7d | expiresIn=7200 秒，refresh 7d 轮换 | 一致 |
| 统一响应格式 | `{code, message, data, traceId}` | 第 2 章完整定义 | 一致 |
| 分页格式 | `{records, total, page, size}` | 第 2 章完整定义 | 一致 |
| 错误码格式 | `LAK-<模块>-<编号>` | 第 7 章完整定义 21 个错误码 | 一致 |
| 业务端口 | 8080 | OpenAPI servers URL 使用 8080 | 一致 |

### 3.2 与 Task 2 (数据库设计) 的字段一致性检查

| 检查项 | Task 2 定义 | Task 3 产出 | 结论 |
|--------|------------|------------|------|
| chat_session 字段 | sessionId, userId, status, intentType, confidence | 全部正确引用 | 一致 |
| chat_message 字段 | messageId, sessionId, role, content, tokens, sourceDocs, confidence | 全部正确引用 | 一致 |
| ticket 字段 | ticketNo, sessionId, complaintType, contactName, contactPhone | 全部正确引用 | 一致 |
| sourceDocs 结构 | `{docId, title, chunk, confidence, page}` | 完全一致 | 一致 |
| SessionStatus 枚举 | 8 个值: NEW~CLOSED | 8 个值完全一致 | 一致 |
| IntentType 枚举 | 5 个值: POLICY_CONSULT~UNKNOWN | 5 个值完全一致 | 一致 |
| MessageRole 枚举 | user/assistant/system | 完全一致 | 一致 |
| TicketStatus 枚举 | PENDING/PROCESSING/COMPLETED/FAILED | 完全一致 | 一致 |
| ComplaintType 枚举 | LAW_ENFORCEMENT/SERVICE_COMPLAINT/DISCIPLINE_VIOLATION/OTHER | 完全一致 | 一致 |
| contactPhone 加密 | 应用层 AES-256, Interface 层明文, Service 层处理 | 文档标注 "Service 层加密存储" | 一致 |
| contactPhone 脱敏 | 无明确定义 | 新增脱敏规则: 138****8000 | 补充性设计 |

### 3.3 关键设计差异说明

1. **DISCIPLINE_VIOLATION vs DISCIPLINE_REPORT**：brief 中使用了 `DISCIPLINE_REPORT`，但根据 Task 2 数据库实际 DDL 定义，枚举值为 `DISCIPLINE_VIOLATION`。接口设计以数据库定义为准，并在文档中显式说明了此差异。

2. **ticket.session_id 唯一索引**：Task 2 将该索引从 `idx_session_id` 改为 `uk_session_id`（唯一索引）。接口设计中 POST /api/v1/tickets 的对应错误码为 `LAK-TICKET-201`（409 冲突），与此设计一致。

3. **contactPhone 脱敏规则**：Task 2 仅在数据库中加密存储，未定义 API 响应时的脱敏展示规则。本接口设计补充了 GET /api/v1/tickets/{ticketNo} 的响应中 `contactPhone` 字段使用 `138****8000` 格式脱敏显示。

4. **SSH 流式输出实现**：由于 SSE 的标准 `EventSource` 仅支持 GET 请求，而本接口为 POST，文档中推荐使用 `Fetch API` + `ReadableStream` 方式实现。同时注明此限制作为 P1 待优化项。

### 3.4 覆盖检查

| 要求章节 | 完成情况 |
|---------|---------|
| 1. API 概览（端点总表 + 全局约束） | 全部端点 11 个，全局约束完整 |
| 2. 统一响应规范（成功/分页/错误/traceId） | 4 个子章节 + 完整 JSON 示例 |
| 3. 认证接口（login/captcha/refresh） | 3 个接口完整设计，含字段校验和错误场景 |
| 4. 对话接口（message/sessions/session detail/delete） | 4 个接口完整设计，含 SSE 流式 |
| 5. 工单接口（create/query） | 2 个接口完整设计，含状态时间线 |
| 6. 管理接口（health/sensitive-words） | 2 个接口完整设计，含权限要求 |
| 7. 错误码体系（5 模块，21 个错误码） | 完整错误码表，含 HTTP 状态码和触发条件 |
| 8. SSE 流式输出规范（事件类型/重连策略/示例代码） | 完整规范，含 JavaScript 消费示例 |
| 附录 A：OpenAPI 3.0 规范 | 完整 YAML，可直接导入 Swagger/Knife4j |

## 4. 对后续编码任务的接口承诺

### 4.1 端点路径清单

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| GET | `/api/v1/health` | 健康检查 | 无 |
| POST | `/api/v1/auth/login` | 登录 | 无 |
| GET | `/api/v1/auth/captcha` | 获取验证码 | 无 |
| POST | `/api/v1/auth/refresh` | 刷新 Token | 无 |
| POST | `/api/v1/chat/message` | 发送消息 | JWT |
| GET | `/api/v1/chat/sessions` | 会话列表 | JWT |
| GET | `/api/v1/chat/sessions/{sessionId}` | 会话历史 | JWT |
| DELETE | `/api/v1/chat/sessions/{sessionId}` | 删除会话 | JWT |
| POST | `/api/v1/tickets` | 创建工单 | JWT |
| GET | `/api/v1/tickets/{ticketNo}` | 查询工单 | JWT |
| POST | `/api/v1/admin/sensitive-words/reload` | 敏感词重载 | JWT + ADMIN |

### 4.2 Request/Response JSON Schema 承诺

每个接口的 Request/Response Schema 已在文档中逐字段定义，包含：
- 字段名（Java 驼峰命名，与数据库 DDL 一致）
- 数据类型（String / Integer / Long / Boolean / Array / Object）
- 必填/可选
- 校验规则（长度、正则、枚举值范围）
- 字段释义

### 4.3 错误码清单（21 个）

| errorCode | HTTP 状态码 | 说明 |
|-----------|------------|------|
| LAK-AUTH-001 | 401 | 未认证 |
| LAK-AUTH-002 | 401 | 用户名或密码错误 |
| LAK-AUTH-003 | 403 | 账户已禁用 |
| LAK-AUTH-004 | 400 | 验证码错误 |
| LAK-AUTH-005 | 400 | 验证码已过期 |
| LAK-AUTH-006 | 401 | 无效的 Refresh Token |
| LAK-AUTH-007 | 401 | Refresh Token 已过期 |
| LAK-AUTH-008 | 401 | Refresh Token 已被使用 |
| LAK-AUTH-009 | 403 | 权限不足 |
| LAK-CHAT-101 | 403 | 敏感词拦截 |
| LAK-CHAT-102 | 429 | 请求限流 |
| LAK-CHAT-103 | 504 | 大模型超时 |
| LAK-CHAT-104 | 404 | 会话不存在 |
| LAK-CHAT-105 | 400 | 消息长度超限 |
| LAK-CHAT-106 | 403 | 无权访问会话 |
| LAK-TICKET-201 | 409 | 会话已创建工单 |
| LAK-TICKET-202 | 500 | 工单创建失败 |
| LAK-TICKET-203 | 404 | 工单不存在 |
| LAK-TICKET-204 | 403 | 无权访问工单 |
| LAK-SYSTEM-500 | 500 | 系统内部错误 |
| LAK-SYSTEM-501 | 503 | 大模型不可用 |
| LAK-SYSTEM-502 | 500 | 敏感词库加载失败 |
| LAK-SYSTEM-503 | 500 | 数据库操作异常 |
| LAK-VALIDATION-601 | 400 | 参数校验失败 |
| LAK-VALIDATION-602 | 400 | 请求体格式错误 |
| LAK-VALIDATION-603 | 415 | 不支持的 Media Type |

### 4.4 编码注意事项

1. **敏感词双向校验**：POST /api/v1/chat/message 在 Service 层实现前缀树匹配 + 大模型语义识别两步敏感词过滤
2. **contactPhone 加解密**：Service 层使用 AES-256 加密写入数据库，读取时解密后脱敏（138****8000）返回
3. **分页参数**：page 从 1 开始，size 最大 100，使用 Mybatis-Plus Page
4. **SSE 流式处理**：后端使用 Spring WebFlux 的 `Flux<ServerSentEvent>` 或 Servlet 3.1 的 `AsyncContext` 实现；前端使用 Fetch API + ReadableStream
5. **审计日志记录**：在 Controller 层或 AOP 层面由 `AuditLogInterceptor` 统一处理，写入 `audit_log_yyyyMM` 分表
6. **traceId**：由 `TraceIdFilter` 在 Filter 层面生成并注入 SLF4J MDC，统一响应体中返回

---

*报告生成时间：2026-06-17*
*Task 3 执行者：AI 架构助手*
