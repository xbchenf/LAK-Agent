# 知识库管理功能 — 轻量级文档管理

> 版本: v1.0 | 日期: 2026-06-19 | 状态: 待评审

## 1. 目标与范围

### 1.1 目标

为 LAK-Agent 平台补齐知识库管理能力，提供文档上传、解析、向量化入库、状态管理、列表查询、删除的完整轻量级闭环。

### 1.2 范围

| 维度 | 决定 |
|------|------|
| 管理粒度 | 轻量文档管理（上传/解析/列表/状态切换/删除） |
| 分组方式 | 沿用现有 `docType` 字段（POLICY / PROCEDURE / TEMPLATE） |
| 文件格式 | TXT + PDF + Word (.docx) |
| 状态管理 | 三状态：DRAFT → ACTIVE → EXPIRED |
| 前端入口 | 独立路由 `/knowledge`，侧边栏新增入口（仅 ADMIN） |
| 权限控制 | 仅 ADMIN 角色，后续细分 |
| 批量操作 | 仅单文档操作 |
| 文件存储 | 本地磁盘 `./data/files/`（生产切换 MinIO 时再抽象接口） |

### 1.3 非范围（本期不做）

- 多知识库分组（KnowledgeBase 实体）
- 批量导入/批量发布/批量停用
- 知识库编辑者角色（KNOWLEDGE_EDITOR）
- MinIO 存储集成
- HTML/Markdown 文件解析
- 知识库健康度统计仪表盘

---

## 2. 后端设计

### 2.1 新增类清单

```
backend/src/main/java/com/lak/ai/
├── controller/
│   └── KnowledgeController              # [新增] REST API
├── service/
│   ├── knowledge/                        # [新增包]
│   │   ├── KnowledgeDocumentService      # 文档 CRUD + 状态管理 + 流水线编排
│   │   ├── DocumentParser                # 文档解析（TXT/PDF/DOCX → 结构化纯文本）
│   │   └── LocalFileStorageService       # 文件存储（本地磁盘，后续抽接口）
│   └── rag/
│       ├── DataIngestionService          # [改造] 接入 DocumentParser，移除硬编码文件读取
│       └── chunker/DocumentChunker       # [复用] 不改动
├── model/
│   ├── dto/
│   │   ├── DocumentUploadDTO             # [新增] 上传参数（docType, effectiveDate, expireDate）
│   │   └── DocumentQueryDTO              # [新增] 列表查询参数（docType, status, keyword, page, size）
│   ├── vo/
│   │   ├── DocumentVO                    # [新增] 文档列表/详情响应
│   │   └── DocumentChunkVO               # [新增] 分块详情响应
│   └── entity/
│       └── KnowledgeDocument             # [已有] 复用，不修改字段
└── mapper/
    └── KnowledgeDocumentMapper           # [已有] 复用，按需增加自定义查询
```

### 2.2 REST API

基础路径: `/api/v1/knowledge`

| 方法 | 路径 | 说明 | 请求体 |
|------|------|------|--------|
| `POST` | `/documents` | 上传文档，保存文件 + 解析 + 分块 + 向量化 + 写DB | multipart/form-data |
| `GET` | `/documents` | 分页列表，支持 docType/status/keyword 筛选 | Query 参数 |
| `GET` | `/documents/{docId}` | 文档详情（含分块数、Qdrant 状态、文件信息） | — |
| `PUT` | `/documents/{docId}` | 编辑文档元信息（标题、有效期） | JSON |
| `PATCH` | `/documents/{docId}/status` | 状态变更：`{ "action": "publish" \| "disable" \| "reactive" }` | JSON |
| `DELETE` | `/documents/{docId}` | 删除文档（同时删文件 + Qdrant 向量 + DB 记录） | — |
| `GET` | `/documents/{docId}/chunks` | 查看文档的分块详情（调试用，从 Qdrant 查询） | — |
| `POST` | `/documents/{docId}/reindex` | 重新解析+向量化（文件内容变更后） | — |

#### 上传请求参数 (`multipart/form-data`)

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | File | 是 | 上传文件（.txt / .pdf / .docx） |
| `docType` | String | 是 | POLICY / PROCEDURE / TEMPLATE |
| `effectiveDate` | String | 否 | 生效日期 yyyy-MM-dd |
| `expireDate` | String | 否 | 过期日期 yyyy-MM-dd |

#### 列表查询参数

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `docType` | String | 否 | 按类型筛选 |
| `status` | String | 否 | 按状态筛选 |
| `keyword` | String | 否 | 标题模糊搜索 |
| `page` | int | 否 | 页码，默认 1 |
| `size` | int | 否 | 每页条数，默认 10 |

### 2.3 文档处理流水线

```
上传文件
  │
  ├─▶ FileStorageService.save(file)
  │     保存原始文件到 ./data/files/{yyyyMM}/{docId}.{ext}
  │     返回文件路径 fileUrl
  │
  ├─▶ DocumentParser.parse(inputStream, fileType)
  │     提取纯文本，保留标题结构标记（如 "第一章 XXX"、"第X条"）
  │     返回结构化文本字符串
  │
  ├─▶ DocumentChunker.chunk(text, docType)
  │     按结构标记分块（800字/块，重叠150字）
  │     返回 List<ChunkResult>
  │
  ├─▶ EmbeddingService.embedForStore() × N
  │     批量向量化，1536维
  │
  ├─▶ QdrantEmbeddingStore.addAll(embeddings)
  │     写入对应 Collection: lak_policy_docs / lak_procedure_docs
  │     （TEMPLATE 类型暂写入 lak_procedure_docs，后续可独立 Collection）
  │
  └─▶ KnowledgeDocumentMapper.insert(doc)
        写入 MySQL: docId, title, docType, fileUrl, chunkCount, status=DRAFT, ...
```

### 2.4 状态机

```
           ┌── publish ─▶ ACTIVE ── disable/expire ──▶ EXPIRED
           │                │                             │
         DRAFT              │                ◀── reactivate
           │                │                             │
           └── disable ─▶ EXPIRED ◀──────────────────────┘
```

| 状态转换 | 触发动作 | Qdrant 操作 |
|---------|---------|------------|
| DRAFT → ACTIVE | 发布 | upsert 所有分块向量 |
| ACTIVE → EXPIRED | 手动停用 或 到期 | delete 所有分块向量 (by docId filter) |
| EXPIRED → ACTIVE | 重新启用 | 重新向量化 + upsert |
| DRAFT → EXPIRED | 废弃草稿 | 无操作（草稿未入库 Qdrant） |
| 任意状态 → 删除 | 删除文档 | delete 向量 + 删文件 + 删 DB 记录 |

定时过期：可选用 `@Scheduled` 定时任务，每小时扫描 `status=ACTIVE AND expire_date < NOW()` 的记录，自动转为 EXPIRED。

### 2.5 DocumentParser 设计

```
DocumentParser (接口)
  ├── parse(InputStream, FileType) → String
  │
  ├── TxtDocumentParser      # 直接读取文本，保留原始换行
  ├── PdfDocumentParser       # Apache PDFBox 提取文本 + 标题结构
  └── DocxDocumentParser      # Apache POI 提取文本 + 标题结构（Heading 样式）
```

- PDF 解析策略：逐页提取文本，检测字号突变作为潜在标题，保留段落边界
- DOCX 解析策略：遍历段落，识别 `Heading` 样式层级，转换为 `#` / `##` 标记
- TXT 解析策略：直接读取，保留换行符用于 `DocumentChunker` 结构识别
- 返回文本统一标记：`## 第X章 XXX` / `### 第X条` / `**表格:**` 等，与现有 `DocumentChunker` 兼容

### 2.6 LocalFileStorageService 设计

```java
@Service
public class LocalFileStorageService {
    // 存储根目录: ./data/files/
    // 路径格式:    {yyyyMM}/{docId}.{ext}
    // 例:         202606/DOC-20260619-0001.pdf

    /** 保存文件，返回存储路径 */
    public String save(MultipartFile file, String docId) { ... }

    /** 读取文件 */
    public InputStream read(String fileUrl) { ... }

    /** 删除文件 */
    public boolean delete(String fileUrl) { ... }
}
```

> 设计决策：先用具体类，不抽接口。将来切 MinIO 时再抽象 `FileStorageService` 接口 + `@Profile` 切换。

### 2.7 异常处理

| 异常 | errorCode | HTTP | 触发条件 |
|------|-----------|------|---------|
| 不支持的格式 | LAK-04-401 | 400 | 上传非 txt/pdf/docx 文件 |
| 文件解析失败 | LAK-04-402 | 500 | PDF/DOCX 损坏或加密 |
| 文档不存在 | LAK-04-403 | 404 | docId 查无记录 |
| 无效状态转换 | LAK-04-404 | 400 | 如 ACTIVE → DRAFT |
| 向量化失败 | LAK-04-405 | 500 | Embedding API 调用失败 |
| Qdrant 写入失败 | LAK-04-406 | 500 | Qdrant 不可用 |

错误码模块编号：`04` = KNOWLEDGE。

### 2.8 审计日志

- 使用现有 `@AuditLog` 注解，记录到 `audit_log_yyyyMM` 表
- 需记录的操作为：上传、删除、发布、停用（状态变更）
- 查看/列表操作不记审计日志

---

## 3. 前端设计

### 3.1 路由

```typescript
{
  path: '/knowledge',
  name: 'knowledge',
  component: () => import('@/views/knowledge/KnowledgeView.vue'),
  meta: { requiresAuth: true, roles: ['ADMIN'] }
},
{
  path: '/knowledge/:docId',
  name: 'knowledge-detail',
  component: () => import('@/views/knowledge/KnowledgeDetail.vue'),
  meta: { requiresAuth: true, roles: ['ADMIN'] }
}
```

侧边栏新增菜单项"知识库管理"（`/knowledge`），仅 `ADMIN` 角色可见。

### 3.2 新增文件

```
frontend/src/
├── views/knowledge/
│   ├── KnowledgeView.vue                # 文档列表页
│   └── KnowledgeDetail.vue              # 文档详情页
├── components/knowledge/
│   ├── DocumentUploadDialog.vue          # 上传弹窗
│   ├── DocumentTable.vue                 # 文档表格
│   └── DocumentStatusTag.vue            # 状态标签
├── api/
│   └── knowledge.ts                     # API 请求封装
├── types/
│   └── knowledge.ts                     # 类型定义
└── stores/
    └── knowledge.ts                     # Pinia store（可选，页面简单可不用）
```

### 3.3 页面布局

**文档列表页 `KnowledgeView.vue`**

- 顶部工具栏：上传按钮 + 类型筛选 Tab（全部/政策/办事指引/模板）+ 关键词搜索框
- 表格列：标题、类型（标签）、状态（标签）、生效日期、过期日期、操作（下拉菜单）
- 状态标签颜色：草稿=灰色、已发布=绿色、已过期=橙色
- 操作菜单：发布/停用（按当前状态动态显示）、查看详情、删除（需确认弹窗）
- 底部 Mybatis-Plus 分页组件

**文档详情页 `KnowledgeDetail.vue`**

- 返回按钮 + 文档标题
- 元信息卡片：文档编号、类型、状态、有效期、分块数、文件大小、创建时间
- 操作按钮：下载原始文件、重新索引、编辑元信息
- 分块列表（可折叠）：每块显示序号、起始文本（截断）、字数

### 3.4 状态颜色映射

| 状态 | 中文 | 标签颜色 |
|------|------|---------|
| DRAFT | 草稿 | default (灰色) |
| ACTIVE | 已发布 | success (绿色) |
| EXPIRED | 已过期 | warning (橙色) |

### 3.5 API 封装

```typescript
// api/knowledge.ts
export function uploadDocument(formData: FormData): Promise<ApiResponse<DocumentVO>>
export function listDocuments(params: DocumentQueryDTO): Promise<ApiResponse<PageResult<DocumentVO>>>
export function getDocument(docId: string): Promise<ApiResponse<DocumentVO>>
export function updateDocument(docId: string, data: Partial<DocumentVO>): Promise<ApiResponse<DocumentVO>>
export function changeStatus(docId: string, action: 'publish' | 'disable' | 'reactivate'): Promise<ApiResponse<void>>
export function deleteDocument(docId: string): Promise<ApiResponse<void>>
export function getChunks(docId: string): Promise<ApiResponse<DocumentChunkVO[]>>
export function reindexDocument(docId: string): Promise<ApiResponse<void>>
```

### 3.6 类型定义

```typescript
// types/knowledge.ts
interface DocumentVO {
  docId: string
  title: string
  docType: 'POLICY' | 'PROCEDURE' | 'TEMPLATE'
  status: 'DRAFT' | 'ACTIVE' | 'EXPIRED'
  fileUrl: string
  fileSize: number
  effectiveDate: string | null
  expireDate: string | null
  chunkCount: number
  createTime: string
  updateTime: string
}

interface DocumentQueryDTO {
  docType?: string
  status?: string
  keyword?: string
  page?: number
  size?: number
}

interface DocumentChunkVO {
  chunkIndex: number
  text: string       // 截断显示，如 200 字符
  textLength: number
}
```

---

## 4. 数据库

### 4.1 复用现有表

`knowledge_document` 表已通过 Flyway `V1__init_schema.sql` 创建，字段完整，无需变更：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 自增主键 |
| doc_id | VARCHAR(32) UK | 文档编号 DOC-yyyyMMdd-xxxx |
| title | VARCHAR(500) | 文档标题（从文件名提取） |
| doc_type | VARCHAR(20) | POLICY / PROCEDURE / TEMPLATE |
| file_url | VARCHAR(500) | 文件存储路径 |
| effective_date | DATE | 生效日期 |
| expire_date | DATE | 过期日期 |
| status | VARCHAR(20) | DRAFT / ACTIVE / EXPIRED |
| chunk_count | INT | 分块数 |
| qdrant_collection | VARCHAR(100) | 对应 Qdrant Collection 名 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |

### 4.2 按需新增索引

```sql
-- 联合筛选加速（列表查询常用组合）
ALTER TABLE knowledge_document ADD INDEX idx_doc_type_status (doc_type, status);
```

### 4.3 Qdrant

复用现有两个 Collection：`lak_policy_docs`（政策）、`lak_procedure_docs`（办事指引）。TEMPLATE 类型暂存入 `lak_procedure_docs`。

---

## 5. 安全与合规

| 要求 | 实现方式 |
|------|---------|
| 认证 | 所有 `/api/v1/knowledge/**` 需 JWT + ADMIN 角色 |
| 审计日志 | 上传/删除/状态变更操作 `@AuditLog` 落库 |
| 文件安全 | 上传校验 MIME 类型 + 魔数，限制文件大小 ≤ 20MB |
| 敏感词 | 不校验文档内容（政务文档本身就是法规，不应拦截） |
| 权限 | Spring Security 配置 `/api/v1/knowledge/**` → `hasRole('ADMIN')` |

---

## 6. 技术选型

| 组件 | 选型 | 说明 |
|------|------|------|
| PDF 解析 | Apache PDFBox 3.0+ | 纯 Java，无外部依赖，适合私有化 |
| DOCX 解析 | Apache POI 5.2+ | 业界标准，读取段落+样式 |
| 文件上传 | Spring MultipartFile | 标准实现 |
| 定时过期 | Spring `@Scheduled` | 每小时扫描一次，简单可靠 |

---

## 7. 已识别的现有一致性问题（本次修复）

| 问题 | 修复方式 |
|------|---------|
| `RagConstants.EMBEDDING_DIMENSION` 和 `EmbeddingService.dimension()` 写死 1536，实际模型返回 1024 | 统一改为 1024；`EmbeddingService.dimension()` 从实际返回值动态获取 |
| `TestDataLoader` 每次启动重复加载 6 份测试数据 | 改为检测 Qdrant 已有数据则跳过 |
| `DataIngestionService` 硬编码文件目录读取 | 重构为通过 `LocalFileStorageService` 和 `DocumentParser` |

---

## 8. 测试要点

| 测试场景 | 验证内容 |
|---------|---------|
| 上传 TXT/PDF/DOCX | 文件保存成功，解析不丢内容，向量入库，DB 记录生成 |
| 上传不支持格式 | 返回 LAK-04-401 |
| 发布文档 | 状态变 ACTIVE，Qdrant 可检索到 |
| 停用文档 | 状态变 EXPIRED，Qdrant 检索不到 |
| 删除文档 | 文件删除，向量删除，DB 记录删除（软删） |
| 重新启用 | 重新向量化，Qdrant 恢复可检索 |
| 分页列表 | 筛选/搜索正确，分页数据正确 |
| 权限拦截 | 非 ADMIN 角色访问返回 403 |
| 定时过期 | 到期文档自动变为 EXPIRED |
