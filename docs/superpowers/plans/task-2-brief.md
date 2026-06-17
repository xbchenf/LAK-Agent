# Task 2: 数据库设计说明书

## Requirements

产出文件：`docs/design/数据库设计说明书.md`

## 项目全局约束（必须遵循）

- 项目名：LAK-Agent，包名：com.lak.ai
- 数据库名：`lak_ai_platform`（MySQL schema 名称）
- MySQL 8.0 + Mybatis-Plus 作为 ORM
- 审计日志 6 个月留存，按月分表
- 等保 2.0 基准（审计日志表禁止 DELETE/UPDATE，仅 INSERT+SELECT）
- 字符集：utf8mb4，排序规则：utf8mb4_unicode_ci
- 所有表必须有 `create_time`、`update_time` 字段（审计日志表例外，仅 `create_time`）
- 主键统一使用 bigint 自增

## Task 1 接口承诺（必须遵守的命名）

以下命名已在系统架构设计说明书中定义，必须使用，不可修改：

**表名**：`chat_session`, `chat_message`, `ticket`, `audit_log`, `knowledge_document`

**审计日志分表规则**：`audit_log_yyyyMM`（如 `audit_log_202601`）

**Redis Key 模式**（供参考，非本Task产出）：
- `session:{sessionId}` (Hash, TTL 1800s)
- `rate_limit:{userId}:{apiPath}` (TTL 60s)

**Qdrant Collection**（供参考）：`lak_policy_docs`, `lak_procedure_docs`，向量维度 1536

## 产出章节要求

### 1. ER 图与实体关系（Mermaid erDiagram）
包含所有核心实体及其关系：
- chat_session ↔ chat_message（1:N）
- chat_session ↔ ticket（1:1，通过 session_id 关联）
- chat_session ↔ audit_log（1:N）
- knowledge_document（独立实体，供 P1 使用，本Task仅定义表结构）
- 预留：sys_user / sys_role / sys_permission（RBAC 基础三表）

### 2. 核心表 DDL（完整可执行的 CREATE TABLE 语句）

每张表必须包含：
- 完整字段列表（字段名、类型、长度、是否NULL、默认值、注释）
- 主键定义
- 索引定义（含索引类型 BTREE/FULLTEXT、联合索引的字段顺序）
- 表注释
- 存储引擎（InnoDB）

#### 2.1 chat_session（对话会话表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | PK |
| session_id | varchar(64) | 会话唯一标识，UUID |
| user_id | bigint | 关联用户 |
| status | varchar(32) | NEW/INTENT_CHECK/ANSWERING/COLLECT_INFO/FALLBACK/COMPLIANCE_CHECK/TICKET_SUBMIT/CLOSED |
| intent_type | varchar(32) | POLICY_CONSULT/PROCEDURE_GUIDE/COMPLAINT_SUGGEST/CHITCHAT/UNKNOWN |
| confidence | decimal(3,2) | 意图识别置信度 |
| create_time | datetime | 创建时间 |
| update_time | datetime | 更新时间 |

索引：`uk_session_id` (session_id)，`idx_user_id` (user_id)，`idx_create_time` (create_time)

#### 2.2 chat_message（消息记录表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | PK |
| session_id | varchar(64) | 关联会话 |
| role | varchar(16) | user/assistant/system |
| content | text | 消息内容 |
| tokens | int | Token 消耗 |
| source_docs | json | 溯源文档列表 |
| confidence | decimal(3,2) | 答复置信度 |
| create_time | datetime | 创建时间 |

索引：`idx_session_id` (session_id)，`idx_create_time` (create_time)

#### 2.3 ticket（工单表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | PK |
| ticket_no | varchar(32) | 工单编号 |
| session_id | varchar(64) | 关联会话 |
| complaint_type | varchar(32) | 投诉类型枚举 |
| contact_name | varchar(64) | 联系人 |
| contact_phone | varchar(20) | 联系电话 |
| description | text | 问题描述 |
| attachment_url | varchar(512) | 附件URL |
| status | varchar(32) | PENDING/PROCESSING/COMPLETED/FAILED |
| external_ticket_id | varchar(64) | 外部工单系统ID |
| create_time | datetime | 创建时间 |
| update_time | datetime | 更新时间 |

索引：`uk_ticket_no` (ticket_no)，`idx_session_id` (session_id)，`idx_status` (status)

#### 2.4 audit_log（审计日志表 — 按月分表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | PK |
| trace_id | varchar(64) | 全链路追踪ID |
| session_id | varchar(64) | 关联会话 |
| user_id | bigint | 用户ID |
| request_body | mediumtext | 请求体 |
| response_body | mediumtext | 响应体 |
| intent_type | varchar(32) | 意图类型 |
| confidence | decimal(3,2) | 置信度 |
| model_params | json | 大模型调用参数 |
| model_response | mediumtext | 大模型原始返回 |
| retrieval_fragments | json | RAG检索召回片段 |
| latency_ms | int | 总耗时 |
| status | varchar(16) | SUCCESS/FAIL/FALLBACK |
| error_message | text | 异常信息 |
| create_time | datetime | 时间戳（分区键） |

索引：`idx_trace_id` (trace_id)，`idx_session_id` (session_id)，`idx_create_time` (create_time)，`idx_status` (status)

⚠️ 审计日志表：仅授予 INSERT + SELECT 权限，禁止 UPDATE/DELETE

#### 2.5 knowledge_document（知识文档元数据 — P1预留）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | PK |
| doc_id | varchar(64) | 文档唯一标识 |
| title | varchar(256) | 文档标题 |
| doc_type | varchar(32) | POLICY/PROCEDURE/TEMPLATE |
| file_url | varchar(512) | 文件存储路径 |
| effective_date | date | 生效日期 |
| expire_date | date | 废止日期（NULL=长期有效） |
| status | varchar(16) | ACTIVE/EXPIRED/DRAFT |
| chunk_count | int | 分段数量 |
| qdrant_collection | varchar(64) | 对应Qdrant Collection |
| create_time | datetime | 创建时间 |
| update_time | datetime | 更新时间 |

索引：`uk_doc_id` (doc_id)，`idx_doc_type` (doc_type)，`idx_status` (status)

#### 2.6-2.8 RBAC 预留表
- sys_user：id, username, password_hash, real_name, email, phone, status, create_time, update_time
- sys_role：id, role_code, role_name, description, create_time, update_time
- sys_permission：id, perm_code, perm_name, resource_path, method, create_time

### 3. 索引策略

- 每个索引的设计理由（覆盖哪个查询场景）
- 联合索引 vs 单列索引的选择理由
- 审计日志表的特殊索引策略（分区表索引）

### 4. 分表与归档策略

- audit_log 按月分表的实现方案（Mybatis-Plus 动态表名拦截器）
- 6 个月数据归档方案（归档 SQL + 定时任务）
- 归档数据的查询策略

### 5. 初始化脚本

提供一个完整的 `init.sql`，包含：
- CREATE DATABASE 语句
- 所有建表语句（当前月份的分表使用示例月份）
- 初始管理员账号 INSERT 语句（密码使用 BCrypt 密文，给出明文供修改）
- 所有索引创建语句

## 质量标准
- DDL 可直接在 MySQL 8.0 中执行无报错
- 所有字段都有合理注释
- 索引设计有场景说明（不只是"加个索引"）
- 敏感字段（密码、手机号）标注加密/脱敏建议
- 字符集、排序规则、存储引擎明确指定
