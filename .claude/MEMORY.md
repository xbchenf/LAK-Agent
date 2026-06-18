# LAK-Agent Memory Index

- [置信度评估三层架构](memory/confidence-architecture.md) — 结构化输出+规则加权+Self-Consistency降级，不使用LogProbs
- [文档分块策略](memory/chunking-strategy.md) — 结构感知段落分块，政策法规用条锚点，办事指南用章节标题锚点
- [Qdrant 混合检索](memory/hybrid-search.md) — Dense+Sparse双向量+RRF融合，无需额外引入ES
- [LangChain4j 版本](memory/langchain4j-version.md) — 1.14.0-beta24，dashscope 坐标为 community 版
- [编码规范](memory/coding-standards.md) — 阿里巴巴Java手册 + LAK硬约束 + Vue3 Element Plus
- [端口与配置](memory/ports-and-config.md) — 8080/8081端口, Redis Key模式, 环境变量清单
