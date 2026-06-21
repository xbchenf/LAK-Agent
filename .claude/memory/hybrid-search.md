---
name: hybrid-search
description: Qdrant 原生混合检索 — Dense+Sparse双向量+RRF融合，无需额外引入ES
metadata:
  type: project
---

# Qdrant 混合检索方案

Qdrant v1.7+ 原生支持混合检索，单组件覆盖向量+关键词。

每个 Collection 存双向量:
- Dense Vector: 1536维, Cosine（text-embedding-v4）
- Sparse Vector: BM25/BM42（Qdrant 原生计算 IDF）

检索流程: Dense检索(Top-10) + Sparse检索(Top-10) → RRF融合(k=60) → Top-5结果
相似度阈值: ≥ 0.75，检索超时 3s。

Payload 含完整溯源元数据（19字段），SourceTracer 组装溯源引用。

**Why:** 之前设计误以为 Qdrant 只支持向量检索，准备引入 ES 做关键词。实际 Qdrant v1.7+ 的 Sparse Vector + RRF 已完全覆盖混合检索需求，架构更简洁。
**How to apply:** 文档入库时同时生成 Dense+Sparse 两种向量。检索始终使用 HybridRetriever，不使用纯向量检索。
