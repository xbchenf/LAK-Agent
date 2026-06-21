---
name: chunking-strategy
description: 文档分块策略 — 结构感知段落分块，政策法规用条锚点，办事指南用章节标题锚点
metadata:
  type: project
---

# 文档分块策略

方案: 结构感知段落分块（Structure-Aware Paragraph Chunking）

主参数: 块大小 500-1000 字符, 最小 200, 最大 1500, 重叠 150 字符。
硬规则: 绝不在句子中间切分。

**政策法规**: 切割锚点 = "第X条"/"第X章"。以条为基本单元，短条合并，长条在款边界拆分。
**办事指南**: 切割锚点 = 数字标题("一、"/"（一）")/关键词标题("办理条件：")。表格保留完整+自动摘要。

每个 chunk 携带完整 Payload: doc_id, title, source_no, article_no, chapter, effective_date, expire_date, keywords, chunk_index, prev/next_chunk_id。

**Why:** 通用 LangChain RecursiveCharacterTextSplitter 按字符数盲切，政法文档"条"被腰斩导致溯源展示时法律条文不完整。本方案以"条"为最小完整单元。
**How to apply:** 两类文档的分块详细配置（正则/YAML）归入 P1 知识库与RAG详细设计文档。编码时读取 `lak.chunking` 配置节点选择对应策略。
