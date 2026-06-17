# Task 4: CLAUDE.md

## Requirements

产出文件：`CLAUDE.md`（项目根目录，AI 编码协作入口文件）

## 项目全局约束（必须在 CLAUDE.md 中体现）

- 项目名：LAK-Agent（Legal Affairs Knowledge Agent Platform / 政法智能知识Agent平台）
- 包名：com.lak.ai，服务名：lak-ai-platform，数据库名：lak_ai_platform
- JDK17 + Spring Boot 3.4.2 + LangChain4j 1.14.0 + Maven 3.8+
- Qdrant（向量库）、Redis 7（会话+限流）、MySQL 8.0（业务+审计）
- 大模型：Qwen3.7-Max（百炼），向量模型：text-embedding-v4（百炼）
- Mybatis-Plus + Resilience4j + SpringDoc
- 政务等保 2.0：6 个月审计日志留存、敏感词双向校验、AI 答复必须溯源
- 所有 AI 答复携带文档来源、文件编号、生效时间
- 会话记忆持久化至 Redis，不使用本地内存
- 置信度 < 0.6 禁止 AI 自动作答，走人工兜底

## 产出要求

CLAUDE.md 是给 AI 编码助手（Claude Code / Codex / Copilot）的上下文文件，需要简洁但充分，覆盖 AI 在编码时需要知道的所有关键信息。不需要长篇大论，重点是**速查**。

### 必须包含的章节

#### 1. 项目概览（3-5行）
- 一句话定位
- 核心业务流程简述

#### 2. 技术栈速查表
- 一张表格列出所有技术组件 + 版本 + 用途

#### 3. 项目结构（目录树）
```
lak-ai-platform/
├── CLAUDE.md
├── docs/
│   ├── design/
│   │   ├── 系统架构设计说明书.md
│   │   ├── 数据库设计说明书.md
│   │   └── 接口设计说明书.md
│   ├── superpowers/
│   │   └── plans/
│   └── 审查报告-政法智能知识Agent平台-2026-06-17.md
├── 政法智能知识Agent平台.md
├── src/main/java/com/lak/ai/
│   ├── agent/           # Agent 编排（主Agent + 子Agent调度器）
│   ├── rag/             # RAG 检索引擎
│   ├── chat/            # 对话管理（会话状态机 + 上下文窗口）
│   ├── ticket/          # 工单模块
│   ├── audit/           # 审计日志
│   ├── security/        # 安全拦截（认证/鉴权/敏感词）
│   │   ├── filter/      #   Filter 实现
│   │   └── config/      #   Security 配置
│   ├── config/          # 全局配置（Redis/Qdrant/Resilience4j）
│   ├── common/          # 公共组件（统一响应/异常/工具类）
│   └── LakAiApplication.java
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   └── db/migration/    # 数据库迁移脚本
├── docker/
│   └── docker-compose.yml
└── pom.xml
```

#### 4. 编码规范与硬约束（来自总纲）
逐条列出，每条用 `- **规则**：说明` 格式：
- 审计日志规范（全量落库、按月分表、禁止删除）
- AI 答复溯源要求（必须有文档来源+文件编号+生效时间）
- 敏感词双向校验（前置 Filter + 后置 Validator）
- 模型调用熔断超时（Resilience4j CircuitBreaker）
- 会话管理约束（Redis 持久化、TTL 30min、无本地内存）
- 低置信度兜底（<0.6 禁止 AI 作答）
- 统一响应格式（ApiResponse + traceId）
- 错误码规范（LAK-<模块>-<编号>）
- 命名规范（包名 com.lak.ai、服务名 lak-ai-platform、数据库 lak_ai_platform）

#### 5. 关键架构决策（来自 Task 1）
- Filter Chain 顺序：SensitiveWord → TraceId → Audit → Auth → RateLimit
- 主Agent 路由机制：意图识别 → 置信度判断（阈值0.6）→ RouteDispatcher
- 会话状态机（8 个状态）
- Agent ID：agent-policy / agent-procedure / agent-complaint

#### 6. 端口与端点速查
- 8080（业务）、8081（管理）
- 核心 API 路径列表
- 白名单端点（/health、/auth/login、/auth/captcha）

#### 7. 配置项速查
- 关键环境变量列表（DASHSCOPE_API_KEY、JWT_SECRET、QDRANT_HOST 等）
- Redis Key 命名规范

#### 8. 文档索引
链接到所有项目文档的表格（文件名 + 路径 + 一句话说明）

## 质量标准
- 总长度控制在 150-250 行，AI 助手可以一次性读完
- 每行都是可操作信息，无废话
- 引用其他文档时使用相对路径（如 `docs/design/系统架构设计说明书.md`）
- 格式为 GitHub Flavored Markdown
