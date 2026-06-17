# Task 3: 接口设计说明书（API Spec）

## Requirements

产出文件：`docs/design/接口设计说明书.md`

## 项目全局约束

- 项目名：LAK-Agent，包名：com.lak.ai
- 业务端口：8080，管理端口：8081
- API 前缀：`/api/v1/`
- JWT 认证：`Authorization: Bearer <token>`，Access Token 2h，Refresh Token 7d
- 统一响应格式：`{ "code": 200, "message": "success", "data": {...}, "traceId": "..." }`
- 分页响应格式：`{ "code": 200, "data": { "records": [...], "total": 100, "page": 1, "size": 20 } }`
- 错误码格式：`LAK-<模块>-<编号>`（如 `LAK-AUTH-001`）

## Task 1 接口承诺（API端点必须匹配）

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/health` | GET | 健康检查（无需认证） |
| `/api/v1/auth/login` | POST | 登录（无需认证） |
| `/api/v1/auth/captcha` | GET | 验证码（无需认证） |
| `/api/v1/chat/message` | POST | 发送消息（支持 SSE） |
| `/api/v1/admin/sensitive-words/reload` | POST | 敏感词热加载（管理接口） |

## Task 2 接口承诺（Entity字段名 + 枚举值）

数据库实体字段名（Java驼峰命名）和枚举值已在 Task 2 产出的 `docs/design/数据库设计说明书.md` 中定义。请先 Read 该文件获取准确的字段列表和枚举常量，确保 Request/Response Schema 与数据库字段完全一致。

关键枚举值速查（来自 Task 1 & 2）：
- SessionStatus: NEW / INTENT_CHECK / ANSWERING / COLLECT_INFO / FALLBACK / COMPLIANCE_CHECK / TICKET_SUBMIT / CLOSED
- IntentType: POLICY_CONSULT / PROCEDURE_GUIDE / COMPLAINT_SUGGEST / CHITCHAT / UNKNOWN
- MessageRole: user / assistant / system
- TicketStatus: PENDING / PROCESSING / COMPLETED / FAILED
- TicketComplaintType: LAW_ENFORCEMENT / SERVICE_COMPLAINT / DISCIPLINE_REPORT / OTHER

## 产出章节要求

### 1. API 概览

列出全部端点表格（路径、方法、认证要求、说明）。

完整端点清单（至少覆盖）：
```
GET    /api/v1/health                       # 健康检查
POST   /api/v1/auth/login                   # 登录
GET    /api/v1/auth/captcha                 # 验证码
POST   /api/v1/auth/refresh                 # 刷新Token
POST   /api/v1/chat/message                 # 发送消息（SSE）
GET    /api/v1/chat/sessions                # 会话列表
GET    /api/v1/chat/sessions/{sessionId}    # 会话历史
DELETE /api/v1/chat/sessions/{sessionId}    # 删除会话
POST   /api/v1/tickets                      # 创建工单
GET    /api/v1/tickets/{ticketNo}           # 查询工单状态
POST   /api/v1/admin/sensitive-words/reload # 敏感词热加载
```

### 2. 统一响应规范

- 成功响应格式（含示例）
- 分页响应格式（含示例）
- 错误响应格式（含示例）
- traceId 说明（用于全链路追踪）

### 3. 认证接口详细设计

#### POST /api/v1/auth/login
- Request Body Schema（username, password, captchaKey, captchaCode）
- 字段校验规则
- Response Body Schema（accessToken, refreshToken, expiresIn, userInfo）
- 错误场景：用户名不存在、密码错误、验证码错误、账户已禁用
- 完整请求/响应 JSON 示例

#### GET /api/v1/auth/captcha
- Response: captchaKey + captchaImage(base64)
- 验证码有效期（5分钟）

#### POST /api/v1/auth/refresh
- Request: refreshToken
- Response: 新的 accessToken + refreshToken

### 4. 对话接口详细设计

#### POST /api/v1/chat/message（核心接口）
- **支持两种模式**：
  - 普通模式：`Accept: application/json` → 返回完整 JSON
  - 流式模式：`Accept: text/event-stream` → SSE 流式返回
- Request Body Schema（sessionId 可选，为空则创建新会话；message 必填）
- SSE 事件格式定义（event: message / done / error）
- Response Body Schema（answer, sources 数组, sessionId, intentType, confidence, ticketNo）
- 错误场景：限流、敏感词拦截、大模型超时、大模型不可用降级

#### GET /api/v1/chat/sessions
- 分页参数（page, size）
- Response: records 数组（sessionId, title, intentType, status, createTime, messageCount）

#### GET /api/v1/chat/sessions/{sessionId}
- Response: session 详情 + message 列表（按时间正序）
- source_docs 字段结构定义

#### DELETE /api/v1/chat/sessions/{sessionId}
- 软删除（设置 status=CLOSED）
- Response: 成功确认

### 5. 工单接口详细设计

#### POST /api/v1/tickets
- Request Body Schema（complaintType, contactName, contactPhone, description）
- 字段校验规则（手机号正则、描述长度限制）
- Response: ticketNo, status, createTime

#### GET /api/v1/tickets/{ticketNo}
- Response: 完整工单信息（含 status 时间线）

### 6. 管理接口详细设计

#### POST /api/v1/admin/sensitive-words/reload
- 权限要求：ADMIN 角色
- Response: 重新加载的词库数量

### 7. 错误码体系

统一错误码格式 `LAK-<模块>-<编号>`，至少覆盖：

| 模块 | 编号范围 | 示例 |
|------|---------|------|
| AUTH | 001-099 | LAK-AUTH-001 未认证 |
| CHAT | 100-199 | LAK-CHAT-101 敏感词拦截 |
| TICKET | 200-299 | LAK-TICKET-201 工单创建失败 |
| SYSTEM | 500-599 | LAK-SYSTEM-501 大模型不可用 |
| VALIDATION | 600-699 | LAK-VALIDATION-601 参数校验失败 |

每个错误码给出：HTTP 状态码、错误消息模板、触发条件

### 8. SSE 流式输出规范

- SSE 事件类型定义（message / done / error）
- 每个事件类型的 data JSON Schema
- 连接超时与重连策略
- 客户端消费示例（JavaScript EventSource 代码片断）

## 质量标准
- 每个接口有完整的 Request/Response JSON 示例
- 字段校验规则明确（必填/可选、长度限制、格式要求、正则）
- 错误场景覆盖完整（正常、异常、边界）
- 建议直接给出 OpenAPI 3.0 YAML 格式的完整 spec（可选，作为附录）
