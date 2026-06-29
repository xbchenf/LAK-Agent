# LAK-Agent 待办事项

> 开发中的已知问题和待完成项，按优先级排列。

---

## P0 — 上线前必须

| 序号 | 事项 | 说明 | 预估 |
|------|------|------|------|
| P0-1 | **知识库法规文档导入** | RAG 问答依赖 Qdrant 向量库中的法规文档。当前 test-docs/ 下有 4 份示例文档（治安管理处罚法、行政复议法、居民身份证法、公安窗口服务规范），需上传至知识库并发布 | 0.5 人天 |
| P0-2 | **审计日志分表自动创建** | 每月 1 日凌晨自动执行 `CREATE TABLE IF NOT EXISTS audit_log_yyyyMM LIKE audit_log_202606`。已有 `@EnableScheduling` 和 `DocumentExpiryScheduler` 参考实现，需新建 `AuditTableScheduler.java` | 0.5 人天 |

## P1 — 高优先级

| 序号 | 事项 | 说明 | 预估 |
|------|------|------|------|
| P1-1 | **监控告警方案** | Prometheus + Grafana 搭建：API P99 延迟、错误率、JVM 内存、DB 连接池、熔断器状态、转人工率、排队等待时长 | 1 人天 |
| P1-2 | **数据备份与灾备** | MySQL 每日全量备份 + Binlog 增量 / Redis RDB / Qdrant 周快照 / MinIO 文件备份 / 灾备恢复 SOP | 1 人天 |
| P1-3 | **审计日志归档** | 每月 1 日凌晨导出 7 个月前分表为 CSV → DROP TABLE。新建 `AuditArchiveScheduler.java` | 0.5 人天 |
| P1-4 | **敏感词管理 CRUD** | 当前仅支持查看列表 + 热加载。需增加新增/删除/编辑敏感词功能，`SensitiveWordView.vue` + 后端 API | 0.5 人天 |
| P1-5 | **工单统计日期修复** | 当前"今日"统计使用 `createTime.startsWith(today)` 字符串前缀匹配，生产环境应改为日期比较 | 0.25 人天 |

## P2 — 中等优先级

| 序号 | 事项 | 说明 | 预估 |
|------|------|------|------|
| P2-1 | **验证码生产环境适配** | 开发环境返回明文 captchaText，生产环境需改为 base64 PNG 图片 | 0.5 人天 |
| P2-2 | **Dashboard 统计仪表盘** | Admin 页面增加今日对话量、意图分布、置信度分布图表（ECharts） | 1 人天 |
| P2-3 | **用户反馈与知识纠错** | AI 答复有用/无用评价 + 知识库纠错反馈闭环 | 1 人天 |
| P2-4 | **Ctrl+K 命令面板** | 全局快捷键命令面板，快速搜索功能页面 | 0.5 人天 |

## P3 — 低优先级

| 序号 | 事项 | 说明 |
|------|------|------|
| P3-1 | **Self-Consistency 投票** | 置信度 0.5-0.7 区间调 LLM 3 次投票决定意图（`needsVoting=true` 标记已设置但未消费） |
| P3-2 | **移动端响应式** | 当前仅基础响应式（768px 侧边栏收缩），需完整移动端适配 |
| P3-3 | **知识库卡片/表格切换** | 知识库列表支持卡片视图和表格视图切换 |
| P3-4 | **坐席技能分组** | 按政策/办事/投诉分类自动分配工单 |
| P3-5 | **坐席工作台实时通知** | 新工单/新排队会话浏览器通知 + 提示音 |

---

## 已知技术债

- `ChatService.java` 约 600 行，可拆分为 `AiChatService` + `HumanChatService`
- 多处硬编码色值未替换为 CSS 变量
- EventSource 改 WebSocket 后，`MessagePushService` 保留了空壳方法（`registerUser`/`registerOperator`）
- `DocumentTable.vue` 和 `DocumentUploadDialog.vue` 仍使用 Element Plus 原生样式，未统一为全局 class
- 前端构建多个 Vite 实例残留端口 5173-5180
