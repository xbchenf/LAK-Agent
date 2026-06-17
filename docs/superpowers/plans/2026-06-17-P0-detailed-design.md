# LAK-Agent P0 详细设计文档产出计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 产出 P0 优先级 4 份详细设计文档 + CLAUDE.md，覆盖架构、数据库、接口、AI 协作入口

**Architecture:** 按依赖顺序产出——先架构（定义全局分层和各组件边界），再数据库（基于架构定义数据模型），再接口（基于数据模型定义 API 契约），最后 CLAUDE.md（索引所有产出文档 + 编码规范速查）

**Tech Stack:** Markdown 文档，无代码产出

## Global Constraints

- 项目名：LAK-Agent（Legal Affairs Knowledge Agent Platform）
- 包名：com.lak.ai，服务名/数据库名：lak_ai_platform
- 服务端口：8080（Spring Boot 默认），管理端口：8081
- 等保 2.0 基准（具体等级待确认，设计时按三级预置扩展点）
- JDK17 + Spring Boot 3.4.2 + LangChain4j 1.14.0
- Qdrant（向量库）、Redis 7（会话+限流）、MySQL 8.0（业务+审计）
- 大模型：Qwen3.7-Max（百炼），向量模型：text-embedding-v4（百炼）
- 政务 6 个月日志留存、敏感词双向校验、所有 AI 答复必须溯源
- 私有化部署，所有组件无外部 SaaS 依赖（百炼 API 除外，需评估网络可达性）

---

### Task 1: 系统架构设计说明书

**Files:**
- Create: `docs/design/系统架构设计说明书.md`

**Produced by this task:**
- 架构分层图（接入层/编排层/能力层/数据层）
- 组件拓扑与交互关系
- 部署拓扑图（开发环境 Docker Compose + 生产环境参考）
- 技术选型说明（每个组件的选型理由 + 备选方案）
- Agent 调度架构（主Agent → 子Agent 路由机制）
- 安全架构总览（认证/鉴权/审计/加密在架构中的位置）
- 扩展性设计（如何新增一个子Agent）

- [ ] **Step 1: 编写架构分层与组件定义**

覆盖以下章节：
```
1. 系统概述
2. 架构分层设计
   2.1 接入层（API Gateway / Interceptor）
   2.2 业务编排层（主Agent + 子Agent调度器）
   2.3 能力层（RAG引擎 / 对话管理 / 工单适配器）
   2.4 数据层（Qdrant / MySQL / Redis / 文件存储）
3. 组件拓扑与交互时序
4. 部署架构
   4.1 开发环境（Docker Compose 编排）
   4.2 生产环境参考（裸机/VM 部署拓扑）
5. 技术选型说明
6. 安全架构集成
7. 扩展性设计
```

- [ ] **Step 2: 绘制架构分层图（ASCII/Mermaid）**

- [ ] **Step 3: 绘制核心链路时序图**

覆盖 3 条核心链路：
- 政策咨询链路：请求 → 前置拦截 → 意图识别 → 政策RAG检索 → 合规校验 → 溯源答复
- 投诉建议链路：请求 → 前置拦截 → 意图识别 → 多轮信息补齐 → 工单创建 → 返回编号
- 低置信度兜底链路：请求 → 前置拦截 → 意图识别（置信度<0.6）→ 人工客服引导

- [ ] **Step 4: 提交**

---

### Task 2: 数据库设计说明书

**Files:**
- Create: `docs/design/数据库设计说明书.md`

**Interfaces:**
- Consumes: 架构分层设计（from Task 1）— 数据层组件定义
- Produces: 核心表 DDL、ER 图、索引策略

- [ ] **Step 1: 编写 ER 图与表关系**

覆盖以下核心实体：
- chat_session（对话会话）
- chat_message（消息记录）
- ticket（工单）
- audit_log（审计日志）
- knowledge_document（知识文档元数据，预留给 P1）
- sys_user / sys_role / sys_permission（预留扩展）

- [ ] **Step 2: 编写完整 DDL**

每张表：字段名、类型、长度、是否必填、默认值、注释、索引

- [ ] **Step 3: 编写索引策略与分表规划**

- 审计日志按月份分表
- 核心查询索引设计
- 数据归档策略

- [ ] **Step 4: 提交**

---

### Task 3: 接口设计说明书（API Spec）

**Files:**
- Create: `docs/design/接口设计说明书.md`

**Interfaces:**
- Consumes: 数据库设计（from Task 2）— 实体定义用于 Request/Response Schema
- Produces: RESTful API 契约（路径、方法、请求/响应体、错误码）

- [ ] **Step 1: 定义核心 API 端点**

```
POST   /api/v1/chat/message          # 发送消息（支持 SSE 流式）
GET    /api/v1/chat/sessions         # 会话列表（分页）
GET    /api/v1/chat/sessions/{id}    # 会话历史
DELETE /api/v1/chat/sessions/{id}    # 删除会话
POST   /api/v1/tickets               # 创建工单
GET    /api/v1/tickets/{id}          # 查询工单状态
GET    /api/v1/health                # 健康检查
```

- [ ] **Step 2: 编写每个接口的详细契约**

每个接口包含：请求方法、路径、请求头、请求体 Schema、响应体 Schema、错误码、示例

- [ ] **Step 3: 编写错误码体系**

统一错误码规范：`LAK-<模块>-<错误编号>`

- [ ] **Step 4: 提交**

---

### Task 4: CLAUDE.md

**Files:**
- Create: `CLAUDE.md`

**Interfaces:**
- Consumes: 所有产出文档（from Tasks 1-3 + 总纲）— 作为路径索引
- Produces: AI 编码协作入口文件

- [ ] **Step 1: 编写项目概览与技术栈速查**

- [ ] **Step 2: 编写项目结构说明**

```
lak-ai-platform/
├── CLAUDE.md
├── docs/
│   ├── design/          # 详细设计文档
│   └── superpowers/     # AI 协作计划与审查
├── src/main/java/com/lak/ai/
│   ├── agent/           # Agent 编排
│   ├── rag/             # RAG 检索
│   ├── chat/            # 对话管理
│   ├── ticket/          # 工单模块
│   ├── audit/           # 审计日志
│   ├── security/        # 安全拦截
│   └── common/          # 公共组件
├── src/main/resources/
└── docker/
```

- [ ] **Step 3: 编写编码规范与约束**

从总纲提取并细化：审计日志规范、溯源要求、敏感词校验要求、熔断超时配置、会话管理约束

- [ ] **Step 4: 编写关键命令速查**

- [ ] **Step 5: 提交**

---
