---
name: coding-standards
description: 编码规范 — 阿里巴巴Java开发手册 + LAK项目硬约束 + Vue3前端的Element Plus
metadata:
  type: project
---

# 编码规范依据

**后端**: 《阿里巴巴Java开发手册（泰山版）》+ LAK 项目 11 条硬约束。
关键规约: Controller→Service→Mapper 分层, DO/DTO/VO/BO 五模型不混用, BusinessException 6子类体系, SLF4J占位符日志, BaseEntity+MetaObjectHandler 自动填充。

**前端（待开发）**: Vue3 + Vite + Element Plus + Pinia + TypeScript。项目结构见 architect design 2.2.3。

**错误码**: 5位数字 LAK-XX-XXX，模块代码: 01=Auth, 02=Chat, 03=Ticket, 98=Validation, 99=System。

**Why:** 政企项目强一致性要求 — 编码风格不允许因人而异。阿里巴巴手册是国内 Java 团队最广泛接受的标准。
**How to apply:** 每次编码前参考 CLAUDE.md 编码规范章节。新增错误码在对应模块范围内递增。
