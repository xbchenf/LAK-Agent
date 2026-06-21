---
name: ports-and-config
description: 端口/配置项速查 — 8080业务 8081管理, Redis Key模式, 环境变量清单
metadata:
  type: reference
---

# 端口与配置速查

**端口**: 8080(业务 API), 8081(Actuator 管理)
**API 前缀**: `/api/v1/`
**包名**: `com.lak.ai`, 服务名: `lak-ai-platform`, 数据库: `lak_ai_platform`

**关键环境变量**:
- `DASHSCOPE_API_KEY` — 百炼 API Key（禁止硬编码）
- `JWT_SECRET` — JWT 签名密钥
- `QDRANT_HOST` — Qdrant 主机
- `REDIS_HOST`, `MYSQL_HOST`

**Redis Key 模式**:
- `session:{sessionId}` Hash TTL 1800s (db0)
- `rate_limit:{userId}:{apiPath}` TTL 60s (db1)
- `captcha:{captchaKey}` TTL 300s (db0)
- `lock:knowledge_update` TTL 300s (db0)

**Token**: Access 2h, Refresh 7d, 验证码 5min。
**Why:** 分散在多个设计文档中，统一记忆文件便于快速查阅。
**How to apply:** 代码中硬编码端口/Key模式直接引用 CommonConstants。环境变量在 docker/.env 和 application.yml 中定义。
