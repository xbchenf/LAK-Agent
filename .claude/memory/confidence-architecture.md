---
name: confidence-architecture
description: 置信度评估三层架构 — 结构化输出+规则加权+Self-Consistency降级
metadata:
  type: project
---

# 置信度评估方案

三层架构，不使用 LogProbs（百炼 Qwen3.7-Max 不支持）。

**第一层: 结构化输出** — Prompt 要求模型返回 JSON `{"intent":"...","confidence":0.85,"reasoning":"..."}`，通过 Few-Shot 校准置信度分布。
**第二层: 规则加权** — 政法术语 Boost(+0.05)、发文字号命中(锁定≥0.85)、投诉关键词锁定、短消息惩罚(−0.10)。
**第三层: 降级** — 灰色地带(0.5~0.7)触发 Self-Consistency 投票(3次)、大模型不可用时 Embedding 质心降级。

路由决策: ≥0.7 直发 / 0.5~0.7 投票 / <0.5 兜底。

**Why:** 政务场景合规底线 — 宁可拒答也不误答。三层组合以最小额外成本换取置信度可靠性。
**How to apply:** 任何新增意图类型或修改 Prompt 时，必须在 Few-Shot 中同步更新置信度校准示例。阈值(0.7/0.5)上线后通过 A/B 测试校准。
