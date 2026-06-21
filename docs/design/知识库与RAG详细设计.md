# 知识库与RAG详细设计

> **项目名称**：LAK-Agent（Legal Affairs Knowledge Agent Platform / 政法智能知识Agent平台）
> **文档版本**：v1.0
> **编制日期**：2026-06-21
> **文档状态**：定稿
> **密级**：内部

---

## 目录

1. [概述](#1-概述)
2. [文档预处理流水线](#2-文档预处理流水线)
3. [文档分块策略](#3-文档分块策略)
4. [向量化与存储](#4-向量化与存储)
5. [混合检索策略](#5-混合检索策略)
6. [溯源机制](#6-溯源机制)
7. [知识库生命周期管理](#7-知识库生命周期管理)
8. [测试数据加载](#8-测试数据加载)
9. [配置参数速查](#9-配置参数速查)

---

## 1. 概述

### 1.1 RAG 引擎定位

RAG（Retrieval-Augmented Generation）引擎是 LAK-Agent 平台的核心能力组件，负责将政法领域的政策法规文档和办事指南文档转化为可检索的知识库，并在用户提问时召回最相关的文档片段，注入大模型上下文以生成带溯源的合规答复。

### 1.2 架构总览

```
文档上传 (PDF/Word/TXT)
  │
  ├─→ DocumentParser.parse()               // 阶段1: 多格式解析 → 纯文本
  │
  ├─→ DocumentChunker.chunk()              // 阶段2: 结构感知分段 → ChunkResult 列表
  │     ├─ POLICY 类型: 按"第X条"锚点切分
  │     └─ PROCEDURE 类型: 按"一、"/"（一）"锚点切分
  │
  ├─→ EmbeddingService.embedForStore()     // 阶段3: 稠密向量化 (1024维)
  │
  └─→ QdrantEmbeddingStore.add()           // 阶段4: 向量 + 元数据入库
        ├─ Collection: lak_policy_docs     (政策法规)
        └─ Collection: lak_procedure_docs  (办事指南)

检索时:
  用户问题
    ├─→ EmbeddingService.embed(query)      // 阶段1: 查询向量化
    ├─→ QdrantEmbeddingStore.search()      // 阶段2: 稠密检索 Top-10 (minScore≥0.75)
    ├─→ KeywordBoosting (Java层)           // 阶段3: 关键词加权融合
    └─→ Top-5 结果                          // 阶段4: 最终片段
```

### 1.3 核心组件清单

| 组件 | 类名 | 职责 |
|------|------|------|
| 文档解析器 | `DocumentParser` | TXT/PDF/DOCX → 纯文本，含 OCR 降级 |
| 文档分块器 | `DocumentChunker` | 结构感知段落分块（按条/按章节） |
| 向量化服务 | `EmbeddingService` | 文本 → 1024维稠密向量 (text-embedding-v4) |
| 混合检索器 | `HybridRetriever` | 稠密检索 + 关键词加权 → Top-5 |
| 溯源追踪器 | `SourceTracer` | 检索片段 → 结构化引用卡片 |
| 数据导入服务 | `DataIngestionService` | 测试数据批量导入到 Qdrant |
| 知识文档服务 | `KnowledgeDocumentService` | 文档全生命周期管理（上传/发布/下架/删除） |
| 文档过期调度器 | `DocumentExpiryScheduler` | 每小时扫描过期文档并自动下架 |

---

## 2. 文档预处理流水线

### 2.1 DocumentParser — 多格式解析

**支持格式**：TXT、PDF、DOCX

| 格式 | 解析引擎 | 策略 |
|------|---------|------|
| **TXT** | Java 原生 `Files.readAllBytes()` | UTF-8 直接读取 |
| **PDF** | Apache PDFBox `PDFTextStripper` | 主路径：文本层提取。若提取文本不足文件大小的 30%（扫描件启发式判定），降级至 OCR |
| **PDF (OCR降级)** | PDFBox `PDFRenderer`(300 DPI) + RapidOCR CLI | 逐页渲染为 PNG → `rapidocr --image_path <file>` 子进程调用 → 120s/page 超时 |
| **DOCX** | Apache POI `XWPFDocument` | 保留 Heading1/2/3 样式 → Markdown `#/##/###`；识别中文结构标题（"第X章"/"第X条"/"一、"等）→ Markdown 标题 |

**文件类型检测**：通过文件扩展名判定，不依赖 MIME 类型。

```java
// DocumentParser.detectType()
.txt  → FileType.TXT
.pdf  → FileType.PDF
.docx → FileType.DOCX
其他   → throw KnowledgeException("不支持的文件类型")
```

### 2.2 文件存储

开发环境使用本地磁盘存储，生产环境使用 MinIO（S3 兼容）。

| 环境 | 实现类 | 存储路径 |
|------|--------|---------|
| 开发 | `LocalFileStorageService` | `{rootDir}/{yyyyMM}/{docId}.{ext}` (默认 `./data/files/`) |
| 生产 | MinIO（预置） | S3 Bucket，路径同左 |

**安全防护**：路径穿越保护 — 所有路径操作前归一化并验证最终路径在 `rootDir` 范围内。

### 2.3 文件上传限制

| 参数 | 值 |
|------|-----|
| 单文件最大 | 20MB |
| 单次请求最大 | 20MB |
| 支持格式 | `.txt` `.pdf` `.docx` |

---

## 3. 文档分块策略

### 3.1 设计原则

政法文档（政策法规、办事指南）具有天然的结构化特征 — 条、款、项、目层级分明，语义边界清晰。分块策略**利用这一结构特征**，而非简单按固定字符数切分。

核心原则：
1. **以语义单元为边界** — 绝不在句子中间切分
2. **结构标记优先** — 利用"第X条"/"第X章"等政法文档特有标记
3. **不同类型不同锚点** — POLICY 按法规条款切分，PROCEDURE 按办事步骤切分
4. **重叠保证上下文连续性** — 每块携带前一块尾部 150 字符作为上下文前缀

### 3.2 分块参数

| 参数 | 建议值 | 环境变量 | 说明 |
|------|--------|---------|------|
| 主块大小 (`CHUNK_SIZE`) | 800 字符 | `LAK_CHUNK_SIZE` | text-embedding-v4 最优编码区间 |
| 最小块大小 (`MIN_SIZE`) | 200 字符 | `LAK_CHUNK_MIN_SIZE` | 短条款合并至上一块，避免碎片化 |
| 最大块大小 (`MAX_SIZE`) | 1500 字符 | `LAK_CHUNK_MAX_SIZE` | 长条款二次切分，在句边界断开 |
| 重叠字符数 (`OVERLAP`) | 150 字符 | `LAK_CHUNK_OVERLAP` | 前向重叠，保证跨块语义连续 |

### 3.3 POLICY 类型分块算法（按"条"切分）

```
输入: 政策法规纯文本
  │
  ├─ Step 1: splitByPattern()
  │   正则: 第[一二三四五六七八九十百千]+[章节条款项]
  │   在匹配位置切分，产生初始段落列表
  │
  ├─ Step 2: mergeShortSegments()
  │   遍历段落，将 < MIN_SIZE(200) 的段落合并到前一段落
  │
  ├─ Step 3: splitLongSegments()
  │   遍历段落，将 > MAX_SIZE(1500) 的段落按 CHUNK_SIZE(800) 切割
  │   切割时检查句子边界（。；！？\n），保证不在句中切断
  │   每块与前一块有 OVERLAP(150) 字符重叠
  │
  └─ Step 4: buildChunks()
      生成 ChunkResult 列表，包含：
      - 块文本 + 重叠前缀
      - 前后块 ID 链 (docId-N-1 ← docId-N → docId-N+1)
      - 元数据：docId, docTitle, docType, sourceNo, effectiveDate
      - version = 1
```

**正则锚点示例**：
```
"第十五条 行政执法机关在作出行政处罚决定之前..." → 切分点
"第三章 行政处罚程序" → 切分点（章节级，产生更大的段落）
```

### 3.4 PROCEDURE 类型分块算法（按"步骤"切分）

```
输入: 办事指南纯文本
  │
  ├─ Step 1: splitByPattern()
  │   正则: [一二三四五六七八九十]+、|（[一二三四五六七八九十]）|(?:办理|申请|提交|领取|缴纳|携带|出具)[^，。；]+[：:]
  │   按"一、"/"二、"、"（一）"/"（二）"、"办理条件："/"申请材料："等锚点切分
  │
  ├─ Step 2-4: 同 POLICY 的合并短段落 + 切割长段落 + 构建块
```

**PROCEDURE 特有锚点示例**：
```
"一、办理条件" → 切分点
"（一）身份证明材料" → 切分点
"申请材料：" → 切分点
"携带以下证件到户籍所在地派出所办理：" → 切分点
```

### 3.5 降级处理

当文档没有任何结构标记（既无"第X条"也无"一、"）时，降级为固定大小分块：
- 按 `CHUNK_SIZE(800)` 固定大小切割，在句子边界断开
- 保留 150 字符重叠
- 最小块 200 字符

### 3.6 分块示例

```
原文（某政策文件第15条）:
"第十五条 行政执法机关在作出行政处罚决定之前，应当告知当事人
作出行政处罚决定的事实、理由及依据，并告知当事人依法享有的权利。
当事人有权进行陈述和申辩。行政执法机关必须充分听取当事人的意见，
对当事人提出的事实、理由和证据，应当进行复核；当事人提出的事实、
理由或者证据成立的，行政执法机关应当采纳。"

分块结果:
┌─ Chunk #42 ───────────────────────────────────────┐
│ Text:                                               │
│  第十五条 行政执法机关在作出行政处罚决定之前，        │
│  应当告知当事人作出行政处罚决定的事实、理由及依据，    │
│  并告知当事人依法享有的权利。                        │
│                                                    │
│ Overlap Prefix (from Chunk #41):                    │
│  "...前款规定适用于所有行政处罚种类。"                 │
│                                                    │
│ Metadata:                                          │
│  docId: "ZFSW-2024-0015"                            │
│  docTitle: "XX省行政执法程序规定"                    │
│  docType: POLICY                                    │
│  sourceNo: "X政发〔2024〕15号"                       │
│  effectiveDate: "2024-06-01"                         │
│  chunkIndex: 42, totalChunks: 98                    │
│  prevChunkId: "ZFSW-2024-0015-41"                   │
│  nextChunkId: "ZFSW-2024-0015-43"                   │
│  version: 1                                         │
└────────────────────────────────────────────────────┘
```

### 3.7 分块时机

| 操作 | 分块时机 | Qdrant 写入 |
|------|---------|------------|
| **上传文档** | 上传时立即分块 | ❌ 不写入（状态=DRAFT） |
| **发布文档** | 状态变更时**重新解析+重新分块** | ✅ 写入 Qdrant |
| **重新索引** | 删除旧向量 → **重新解析+重新分块** → 重新写入 | ✅ 写入 Qdrant |
| **定时过期** | 不重新分块，直接删除 Qdrant 向量 | ❌ 删除 |

> **设计决策**：DRAFT 状态文档不索引到 Qdrant，仅存储元数据到 MySQL。发布时才执行完整的解析→分块→向量化→入库流程。这保证了检索结果只包含已发布的正式文档。

---

## 4. 向量化与存储

### 4.1 Embedding 服务

| 项目 | 说明 |
|------|------|
| **模型** | 百炼 text-embedding-v4 |
| **维度** | 1024 维 |
| **距离度量** | Cosine |
| **超时** | 10s |
| **实现** | LangChain4j `QwenEmbeddingModel` → Spring Bean `EmbeddingModel` |

**EmbeddingService 接口**：

```java
public class EmbeddingService {
    /** 嵌入文本，返回 float[] */
    float[] embed(String text);

    /** 嵌入文本，返回 LangChain4j Embedding 对象（用于直接写入 Qdrant） */
    Embedding embedForStore(String text);

    /** 返回向量维度 */
    int dimension();  // 1024
}
```

### 4.2 Qdrant Collection 设计

系统使用两个独立的 Qdrant Collection，按知识类型逻辑隔离：

| Collection | 用途 | 向量维度 | 距离 | 使用方 |
|-----------|------|---------|------|--------|
| `lak_policy_docs` | 政法政策法规 | 1024 | Cosine | PolicyAgent (子AgentA) |
| `lak_procedure_docs` | 公安办事指南 | 1024 | Cosine | ProcedureAgent (子AgentB) |

> **设计决策**：使用两个独立 Collection 而非单 Collection + type 过滤，原因是：(1) 两类文档的检索需求明确隔离，不存在跨类型混合检索场景；(2) 独立 Collection 可独立调优索引参数；(3) 避免 type 过滤带来的查询性能开销。

### 4.3 Qdrant Point Payload Schema

每条 chunk 对应一个 Qdrant Point，Payload 承载分块元数据与溯源信息：

```json
{
  "doc_id": "ZFSW-2024-0015",
  "doc_title": "XX省行政执法程序规定",
  "doc_type": "POLICY",
  "source_no": "X政发〔2024〕15号",
  "article_no": "第十五条",
  "chapter": "第三章 行政处罚程序",
  "effective_date": "2024-06-01",
  "chunk_index": 42,
  "prev_chunk_id": "ZFSW-2024-0015-41",
  "next_chunk_id": "ZFSW-2024-0015-43"
}
```

| 字段 | 类型 | 说明 | 用途 |
|------|------|------|------|
| `doc_id` | keyword | 文档唯一标识 | 溯源引用、知识更新定位 |
| `doc_title` | text | 文档标题 | 检索过滤、溯源展示 |
| `doc_type` | keyword | POLICY / PROCEDURE | Collection 内按类型过滤 |
| `source_no` | keyword | 发文字号 | 精确匹配检索 |
| `article_no` | keyword | 条号 | 精确条文定位 |
| `chapter` | keyword | 所属章节 | 结构化溯源展示 |
| `effective_date` | keyword | 生效日期 | 时效性过滤 |
| `chunk_index` | integer | 块序号 | 相邻块导航 |
| `prev_chunk_id` | keyword | 上一块 ID | 上下文扩展检索 |
| `next_chunk_id` | keyword | 下一块 ID | 上下文扩展检索 |

### 4.4 Qdrant 连接配置

| 参数 | 值 |
|------|-----|
| Host | `localhost`（开发）/ `qdrant.internal`（生产） |
| gRPC Port | 6334 |
| REST Port | 6333 |
| 连接超时 | 10s |
| 索引类型 | HNSW |
| HNSW M 值 | 16 |
| HNSW ef_construct | 100 |
| API Key | 无（内网部署） |

### 4.5 Collection 初始化

应用启动时（`@PostConstruct`），`QdrantCollectionInitializer` 自动检查并创建 Collection：

```java
// 幂等操作 — Collection 已存在则跳过
if (!client.collectionExists(collectionName)) {
    client.createCollection(collectionName,
        VectorParams.newBuilder()
            .setSize(1024)
            .setDistance(Distance.Cosine)
            .build());
}
```

---

## 5. 混合检索策略

### 5.1 检索流程

```
用户问题 (query)
  │
  ├─ Step 1: EmbeddingService.embed(query) → 1024维稠密向量
  │
  ├─ Step 2: QdrantEmbeddingStore.search(vector, maxResults=10, minScore=0.75)
  │    返回: Top-10 稠密检索结果 (按 Cosine 相似度降序)
  │
  ├─ Step 3: KeywordBoosting (Java 层关键词加权)
  │    - tokenize(query) → 单字 + 字母数字词集合
  │    - 对每个片段: 计算匹配 Token 数 / 总 Token 数 = 命中率
  │    - boost = min(命中率 × 片段总词数 × 0.05, 1.0)
  │    - 片段得分 = 原始相似度 × (1 + boost)
  │
  ├─ Step 4: 按新得分降序排列，过滤 score < 0.75
  │
  └─ Step 5: 取 Top-5 作为最终检索结果
```

### 5.2 关键词加权算法

```java
// HybridRetriever.tokenize()
// CJK字符: 每个字为一个 Token (Unicode > 0x007F 的字符)
// 英文/数字: 按空格/标点分词，转小写
// 示例: "行政复议 时效" → {"行","政","复","议","时","效"}
// 示例: "身份证办理" → {"身","份","证","办","理"}

// Boost 计算:
for (RagFragment frag : fragments) {
    Set<String> fragTokens = tokenize(frag.getText());
    long matches = queryTokens.stream().filter(fragTokens::contains).count();
    double boost = Math.min(matches * 0.05, 1.0);  // 每个命中 +0.05，上限 1.0
    frag.setScore(frag.getScore() * (1 + boost));
}
```

### 5.3 检索参数

| 参数 | 值 | 常量 |
|------|-----|------|
| 稠密检索 Top-K | 10 | `DENSE_TOP_K = 10` |
| 融合后 Top-K | 5 | `FUSION_TOP_K = 5` |
| 相似度阈值 | ≥ 0.75 | `SIMILARITY_THRESHOLD = 0.75` |
| 最大检索超时 | 3s | `RETRIEVAL_TIMEOUT_SECONDS = 3` |
| RRF 融合常数 | 60 | `RRF_K = 60`（预留给未来真正的混合检索） |

### 5.4 当前实现说明

> **注意**：当前阶段的关键词加权在 Java 代码中实现，属于**简化版混合检索**。Qdrant 原生支持 Named Vectors + Sparse Vector (BM25/BM42) + RRF FusionQuery，这是架构预留的未来升级路径。当前简化方案的优势是零额外 Embedding 调用成本，且在中文字符级匹配场景下效果足够。

### 5.5 错误处理

`HybridRetriever.search()` 对所有异常采用**静默降级**策略：

```java
try {
    // 检索逻辑
} catch (Exception e) {
    log.error("混合检索失败: {}", e.getMessage(), e);
    return Collections.emptyList();  // 不抛异常，返回空列表
}
```

这种策略保证了即使 Qdrant 不可用，对话链路不会中断（上游 Agent 收到空结果后进入兜底）。

---

## 6. 溯源机制

### 6.1 SourceTracer

`SourceTracer` 负责将检索片段转化为结构化的溯源引用，并校验 AI 答复的溯源完整性。

**核心方法**：

| 方法 | 说明 |
|------|------|
| `buildCitations(List<RagFragment>)` | 检索片段 → `List<SourceCitation>` |
| `validateHasSources(List<RagFragment>)` | 验证至少有一片同时具备 `docId` 和 `sourceNo` |

### 6.2 SourceCitation 结构

```java
public record SourceCitation(
    String docId,        // 文档标识
    String docTitle,     // 文档标题
    String sourceNo,     // 发文字号
    String articleNo,    // 条号
    String chapter,      // 所属章节
    String effectiveDate,// 生效日期
    String fragment,     // 原文片段
    Double score         // 检索得分
) {
    /** 转为前端展示用的 Map（只含非 null 字段） */
    public Map<String, Object> toMap() { ... }
}
```

### 6.3 溯源的三个环节

```
1. 检索阶段 (PolicyAgentTools.search)
   HybridRetriever → RagFragment 列表
   → SourceTracer.buildCitations() → 注入 System Prompt 的 {{docs}} 占位符
   → 格式化: 【sourceNo 第articleNo条】(生效: effectiveDate) fragment_text

2. 生成阶段 (PolicyAgentService.answer)
   LLM 在 System Prompt 中收到指令:
   "使用提供的检索材料作答，引用时注明文档编号和条款号，勿做元描述"

3. 合规阶段 (ComplianceValidator.validate)
   非 FALLBACK 场景下，必须至少有一片同时具备 docId + sourceNo
   → 否则标记为"溯源不完整"，替换为安全兜底答复
```

### 6.4 溯源在前端的展示

每条 AI 答复消息（`chat_message` 表）的 `source_docs` 字段存储 JSON 数组：

```json
[
  {
    "docId": "DOC-2026-00123",
    "title": "XX市行政诉讼程序规定（2026版）",
    "chunk": "第三十五条 当事人对一审判决不服的...",
    "confidence": 0.92
  }
]
```

前端 `SourceCitation.vue` 组件以卡片形式展示，用户可点击展开查看原文片段。

---

## 7. 知识库生命周期管理

### 7.1 文档状态机

```
        上传
         │
         ▼
     ┌───────┐  publish   ┌────────┐  disable/expire  ┌─────────┐
     │ DRAFT │ ────────→  │ ACTIVE │ ───────────────→ │ EXPIRED │
     └───┬───┘            └────┬───┘                  └────┬────┘
         │   delete            │   reactivate              │  delete
         ▼                     ▼                           ▼
       [删除]              ┌─────────┐                 [删除]
                          │ ACTIVE  │
                          └─────────┘
```

| 状态 | Qdrant 索引 | 可检索 | 允许操作 |
|------|-----------|--------|---------|
| **DRAFT** | ❌ 未索引 | ❌ | publish, delete |
| **ACTIVE** | ✅ 已索引 | ✅ | disable, delete |
| **EXPIRED** | ❌ 已移除 | ❌ | reactivate, delete |

### 7.2 操作详述

#### 上传 (upload)
1. 验证文件类型和大小（≤20MB）
2. 保存文件到本地磁盘 / MinIO
3. 解析文档 → 分块 → 统计块数
4. 写入 MySQL `knowledge_document` 表（status=DRAFT）
5. **不写入 Qdrant**

#### 发布 (publish)
1. 验证当前状态为 DRAFT
2. 从磁盘重新读取原始文件
3. 重新解析 → 重新分块
4. 逐块调用 `EmbeddingService.embedForStore()` → `QdrantEmbeddingStore.add()`
5. 更新 MySQL status=ACTIVE

#### 下架 (disable)
1. 验证当前状态为 ACTIVE
2. 从两个 Qdrant Collection 中按 `docId` 过滤并删除全部 Point
3. 更新 MySQL status=EXPIRED

#### 重新激活 (reactivate)
1. 验证当前状态为 EXPIRED
2. 重新执行发布流程（重新解析 → 分块 → 向量化 → 入库）
3. 更新 MySQL status=ACTIVE

#### 删除 (delete)
1. 删除 MySQL 记录（事务内）
2. 尝试清理 Qdrant 向量（失败仅记录日志，不回滚 DB）
3. 删除磁盘文件（失败仅记录日志）

### 7.3 文档 ID 生成

格式：`DOC-{yyyyMMdd}-{0001..9999}`

每天从 0001 开始自增，启动时查询当日已有文档数确定起始序号。

### 7.4 定时过期

`DocumentExpiryScheduler` — 每小时整点执行 (`@Scheduled(cron = "0 0 * * * *")`)：

1. 查询 `status = ACTIVE AND expire_date < CURRENT_DATE` 的文档
2. 逐文档：删除 Qdrant 向量 → 更新 status=EXPIRED
3. 单文档失败不影响批处理继续

---

## 8. 测试数据加载

### 8.1 TestDataLoader

`CommandLineRunner`，在应用启动时执行，受 `lak.test-data.load-on-startup` 开关控制。

**加载逻辑**：
1. 通过 gRPC `QdrantClient.countAsync()` 检查 Collection 中是否已有数据
2. 若 count > 0，跳过全部加载（幂等保护）
3. 若 count = 0，依次加载测试文件

### 8.2 测试数据集

| Collection | 文件 | 内容 |
|-----------|------|------|
| `lak_policy_docs` | 治安管理处罚法实施条例 | 政法政策法规示例 |
| `lak_policy_docs` | 旅馆业治安管理办法 | 政法政策法规示例 |
| `lak_procedure_docs` | 身份证办理指南 | 办事指引示例 |
| `lak_procedure_docs` | 无犯罪记录证明办理 | 办事指引示例 |
| `lak_procedure_docs` | 户口迁移办理指南 | 办事指引示例 |
| `lak_procedure_docs` | 居住证办理指南 | 办事指引示例 |

### 8.3 测试文件格式

```
文档标题
发文字号: XX发〔2024〕XX号
发布机构: XX省人民政府
发布日期: 2024-05-15
生效日期: 2024-06-01
----
（正文内容，从下一行开始）
...
```

`DataIngestionService.parseMetadata()` 提取前 5 行的标题、发文字号和生效日期，`----` 之后为正文内容。

---

## 9. 配置参数速查

### 9.1 application.yml 关键配置

```yaml
# DashScope 模型
langchain4j.dashscope.chat-model:
  model-name: qwen3.7-max
  temperature: 0.1
  max-tokens: 4096
  timeout: 30s

langchain4j.dashscope.embedding-model:
  model-name: text-embedding-v4
  timeout: 10s

# LAK 自定义
lak:
  rag:
    retrieval-timeout-seconds: 3
  test-data:
    load-on-startup: true
  storage:
    local-path: ./data/files

# 文件上传
spring.servlet.multipart:
  max-file-size: 20MB
  max-request-size: 20MB
```

### 9.2 环境变量

| 变量 | 必需 | 说明 |
|------|------|------|
| `DASHSCOPE_API_KEY` | ✅ | 百炼 API 密钥 |
| `QDRANT_HOST` | ❌ (dev 默认 localhost) | Qdrant 主机地址 |
| `LAK_CHUNK_SIZE` | ❌ (默认 800) | 分块大小 |

### 9.3 RagConstants 常量

```java
// Collection
String COLLECTION_POLICY = "lak_policy_docs";
String COLLECTION_PROCEDURE = "lak_procedure_docs";

// Embedding
int EMBEDDING_DIMENSION = 1024;

// 检索
int DENSE_TOP_K = 10;
int FUSION_TOP_K = 5;
double SIMILARITY_THRESHOLD = 0.75;
int RRF_K = 60;
int RETRIEVAL_TIMEOUT_SECONDS = 3;
```

---

*文档结束。v1.0 — 2026-06-21*
