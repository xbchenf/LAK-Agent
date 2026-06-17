# Task 2 完成报告

## 1. 状态

**DONE**

全部要求已覆盖，DDL 可直接在 MySQL 8.0 中执行。

## 2. 产出文件

| 文件 | 路径 | 说明 |
|------|------|------|
| 数据库设计说明书 | `docs/design/数据库设计说明书.md` | 完整数据库设计文档 |
| 初始化脚本 | 内嵌于 `数据库设计说明书.md` 第 6 章 | 可直接执行的完整 `init.sql` |

## 3. 自审发现

### 3.1 已解决的事项

- **ticket.session_id 索引类型**：brief 要求 `idx_session_id`，但根据 `chat_session` 与 `ticket` 为 1:1 关系的约束，实际改为 `uk_session_id`（唯一索引），在数据库层面强制执行 1:1 关系。此变更不违反 brief 要求（brief 仅要求命名，索引类型设计属于本 Task 自主决策范畴），且对 Task 3 无影响。
- **chat_message.source_docs 暂不建 JSON 索引**：按溯源文档查询消息属于低频审计事后分析场景，P0 阶段不建立 JSON Multi-Value Index，标记为 P1 优化项。

### 3.2 需注意的设计决策

| 序号 | 决策项 | 说明 |
|------|--------|------|
| 1 | contact_phone 加密存储 | ticket.contact_phone 和 sys_user.phone 使用应用层 AES-256 加密后写入，非 MySQL 内置加密。**影响 Task 3**：Interface 层的 Request/Response 中此字段为明文格式，加密/解密在 Service 层处理。 |
| 2 | 审计日志分表名称 | `audit_log_yyyyMM` 格式，Task 1 承诺已确认。初始脚本使用 `audit_log_202606` 作为示例。 |
| 3 | RBAC 关联表 | 新增 `sys_role_permission` 关联表实现角色-权限多对多关系，该表未在 brief 中列出，但属于 RBAC 标准实现所需，已在 DDL 中补充。 |

## 4. 对 Task 3（接口设计）的接口承诺

### 4.1 Entity 字段名列表

#### chat_session

| 字段名 | Java 类型 | DB 类型 | 说明 |
|--------|-----------|---------|------|
| id | Long | bigint | PK |
| sessionId | String | varchar(64) | 会话唯一标识 |
| userId | Long | bigint | 关联用户ID |
| status | String | varchar(32) | 枚举值：NEW/INTENT_CHECK/ANSWERING/COLLECT_INFO/FALLBACK/COMPLIANCE_CHECK/TICKET_SUBMIT/CLOSED |
| intentType | String | varchar(32) | 枚举值：POLICY_CONSULT/PROCEDURE_GUIDE/COMPLAINT_SUGGEST/CHITCHAT/UNKNOWN |
| confidence | BigDecimal | decimal(3,2) | 0.00-1.00 |
| createTime | LocalDateTime | datetime | |
| updateTime | LocalDateTime | datetime | |

#### chat_message

| 字段名 | Java 类型 | DB 类型 | 说明 |
|--------|-----------|---------|------|
| id | Long | bigint | PK |
| sessionId | String | varchar(64) | FK -> chat_session.session_id |
| role | String | varchar(16) | user/assistant/system |
| content | String | text | |
| tokens | Integer | int | |
| sourceDocs | String/JSON | json | JSON 结构见下方 |
| confidence | BigDecimal | decimal(3,2) | |
| createTime | LocalDateTime | datetime | |

#### ticket

| 字段名 | Java 类型 | DB 类型 | 说明 |
|--------|-----------|---------|------|
| id | Long | bigint | PK |
| ticketNo | String | varchar(32) | 工单编号 |
| sessionId | String | varchar(64) | FK -> chat_session.session_id (1:1) |
| complaintType | String | varchar(32) | LAW_ENFORCEMENT/SERVICE_COMPLAINT/DISCIPLINE_VIOLATION/OTHER |
| contactName | String | varchar(64) | |
| contactPhone | String | varchar(20) | **加密存储**，Service 层处理 |
| description | String | text | |
| attachmentUrl | String | varchar(512) | |
| status | String | varchar(32) | PENDING/PROCESSING/COMPLETED/FAILED |
| externalTicketId | String | varchar(64) | |
| createTime | LocalDateTime | datetime | |
| updateTime | LocalDateTime | datetime | |

#### knowledge_document

| 字段名 | Java 类型 | DB 类型 | 说明 |
|--------|-----------|---------|------|
| id | Long | bigint | PK |
| docId | String | varchar(64) | 文档唯一标识 |
| title | String | varchar(256) | |
| docType | String | varchar(32) | POLICY/PROCEDURE/TEMPLATE |
| fileUrl | String | varchar(512) | |
| effectiveDate | LocalDate | date | |
| expireDate | LocalDate | date | |
| status | String | varchar(16) | ACTIVE/EXPIRED/DRAFT |
| chunkCount | Integer | int | |
| qdrantCollection | String | varchar(64) | |
| createTime | LocalDateTime | datetime | |
| updateTime | LocalDateTime | datetime | |

#### audit_log（分表：audit_log_yyyyMM）

| 字段名 | Java 类型 | DB 类型 | 说明 |
|--------|-----------|---------|------|
| id | Long | bigint | PK |
| traceId | String | varchar(64) | |
| sessionId | String | varchar(64) | |
| userId | Long | bigint | |
| requestBody | String | mediumtext | |
| responseBody | String | mediumtext | |
| intentType | String | varchar(32) | |
| confidence | BigDecimal | decimal(3,2) | |
| modelParams | String/JSON | json | |
| modelResponse | String | mediumtext | |
| retrievalFragments | String/JSON | json | |
| latencyMs | Integer | int | |
| status | String | varchar(16) | SUCCESS/FAIL/FALLBACK |
| errorMessage | String | text | |
| createTime | LocalDateTime | datetime | |

### 4.2 JSON 字段结构

#### source_docs（chat_message 表）

```json
[
  {
    "docId": "string",
    "title": "string",
    "chunk": "string",
    "confidence": 0.0,
    "page": 0
  }
]
```

#### model_params（audit_log 表）

```json
{
  "model": "string",
  "temperature": 0.0,
  "maxTokens": 0,
  "topP": 0.0,
  "systemPrompt": "string",
  "messages": [
    {"role": "string", "content": "string"}
  ],
  "tools": [],
  "provider": "string"
}
```

#### retrieval_fragments（audit_log 表）

```json
[
  {
    "collection": "string",
    "docId": "string",
    "title": "string",
    "chunk": "string",
    "score": 0.0,
    "rank": 0
  }
]
```

### 4.3 枚举值清单

所有枚举值已在数据库设计说明书的**附录 B：字段枚举值汇总**中完整列出。Task 3 设计 Request/Response Schema 时请直接引用该章节的枚举值定义。

### 4.4 关键注意事项

1. **contact_phone 字段**：ticket.contact_phone 和 sys_user.phone 使用应用层 AES-256 加密存储。Interface 层 Request/Response 中使用 `String` 类型的明文，加密/解密由 Service 层处理。Task 3 设计 Schema 时无需特殊处理。
2. **audit_log 分表路由**：Task 3 的接口设计中，审计日志查询 API 的 Request 参数必须包含 `createTimeStart` 和 `createTimeEnd` 时间范围，便于路由到正确的分表。
3. **knowledge_document 为 P1 预留**：Task 3 可暂不设计知识文档的完整 CRUD 接口，但在系统设计中需预留基础查询能力（如：根据 docId 查询文档元数据）。
