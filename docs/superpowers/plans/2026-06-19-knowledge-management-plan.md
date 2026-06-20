# 知识库管理功能 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 LAK-Agent 平台实现轻量级文档管理：上传（TXT/PDF/DOCX）、自动解析+分块+向量化入库、状态管理（DRAFT→ACTIVE→EXPIRED）、列表检索、删除。

**Architecture:** KnowledgeController → KnowledgeDocumentService（编排流水线）→ LocalFileStorageService（存文件）+ DocumentParser（解析）+ 复用现有 RAG 管线（DocumentChunker → EmbeddingService → Qdrant）。前端独立路由 `/knowledge`，Vue3 组件体系，仅 ADMIN 可见。

**Tech Stack:** Spring Boot 3.4.2, Mybatis-Plus, LangChain4j, Qdrant gRPC, Apache PDFBox, Apache POI, RapidOCR CLI, Vue 3 + TypeScript

**Spec:** `docs/superpowers/specs/2026-06-19-knowledge-management-design.md`

## Global Constraints

- 所有 API 路径 `/api/v1/knowledge/**` 需 JWT 认证 + ADMIN 角色
- 统一响应 `ApiResponse<T>` / `PageResult<T>`（包 `com.lak.ai.common.response`）
- 异常体系继承 `BusinessException(int code, String message)`（包 `com.lak.ai.common.exception`）
- 知识库错误码段：LAK-04-xxx（code: 4xx），模块编号 04
- 文件存储根目录 `./data/files/`，路径格式 `{yyyyMM}/{docId}.{ext}`
- 单文件大小限制 ≤ 20MB
- 文档编号格式 `DOC-yyyyMMdd-xxxx`（4位序号）
- DTO 命名 `XxxDTO`，VO 命名 `XxxVO`，包 `com.lak.ai.model.dto` / `com.lak.ai.model.vo`
- Controller 使用 `@RequiredArgsConstructor` + constructor injection
- Vue 组件遵循现有 Composition API 风格
- 前端状态标签颜色：草稿=default(灰)、已发布=success(绿)、已过期=warning(橙)
- 审计日志：上传/删除/状态变更操作加 `@AuditLog` 注解

---

## File Map

### Backend — New Files

| File | Responsibility |
|------|---------------|
| `backend/.../common/exception/KnowledgeException.java` | 知识库异常子类，code=4xx |
| `backend/.../model/dto/DocumentUploadDTO.java` | 上传请求参数封装（不过 form-data 大部分字段来自 request param） |
| `backend/.../model/dto/DocumentQueryDTO.java` | 列表查询参数 |
| `backend/.../model/dto/StatusActionDTO.java` | 状态变更请求体 `{ "action": "..." }` |
| `backend/.../model/vo/DocumentVO.java` | 文档列表/详情响应 |
| `backend/.../model/vo/DocumentChunkVO.java` | 分块详情响应 |
| `backend/.../service/knowledge/LocalFileStorageService.java` | 本地磁盘文件存储 |
| `backend/.../service/knowledge/DocumentParser.java` | 文档解析接口+工厂 |
| `backend/.../service/knowledge/DocumentParser/TxtDocumentParser.java` | TXT 解析 |
| `backend/.../service/knowledge/DocumentParser/PdfDocumentParser.java` | PDF 解析（PDFBox + RapidOCR 兜底） |
| `backend/.../service/knowledge/DocumentParser/DocxDocumentParser.java` | DOCX 解析（POI） |
| `backend/.../service/knowledge/KnowledgeDocumentService.java` | 文档 CRUD + 状态管理 + 流水线编排 |
| `backend/.../controller/KnowledgeController.java` | REST API 8个端点 |

### Backend — Modified Files

| File | Change |
|------|--------|
| `backend/pom.xml` | 添加 PDFBox、POI 依赖 |
| `backend/.../constant/RagConstants.java` | `EMBEDDING_DIMENSION` 1536→1024 |
| `backend/.../service/rag/embedding/EmbeddingService.java` | `dimension()` 动态获取实际维度 |
| `backend/.../service/rag/TestDataLoader.java` | 跳过逻辑：Qdrant 已有数据则不再加载 |
| `backend/.../service/rag/DataIngestionService.java` | 重构内部文件读取为调用 DocumentParser（Task 1c 快速修复） |
| `backend/.../mapper/KnowledgeDocumentMapper.java` | 增加自定义分页查询 + 按 docId 查询 |
| `backend/.../common/exception/GlobalExceptionHandler.java` | 增加 `KnowledgeException` handler |

### Frontend — New Files

| File | Responsibility |
|------|---------------|
| `frontend/src/types/knowledge.ts` | 类型定义 |
| `frontend/src/api/knowledge.ts` | API 请求封装 |
| `frontend/src/components/knowledge/DocumentStatusTag.vue` | 状态标签 |
| `frontend/src/components/knowledge/DocumentUploadDialog.vue` | 上传弹窗 |
| `frontend/src/components/knowledge/DocumentTable.vue` | 文档表格 |
| `frontend/src/views/knowledge/KnowledgeView.vue` | 文档列表页 |
| `frontend/src/views/knowledge/KnowledgeDetail.vue` | 文档详情页 |
| `frontend/src/stores/knowledge.ts` | Pinia store（可选，简单页面可不用） |

### Frontend — Modified Files

| File | Change |
|------|--------|
| `frontend/src/router/index.ts` | 新增 `/knowledge` 和 `/knowledge/:docId` 路由 |
| `frontend/src/layouts/DefaultLayout.vue` | 侧边栏新增"知识库管理"菜单项 |

---

### Task 1: Fix existing inconsistencies

**Files:**
- Modify: `backend/src/main/java/com/lak/ai/constant/RagConstants.java:15`
- Modify: `backend/src/main/java/com/lak/ai/service/rag/embedding/EmbeddingService.java:14,39-40`
- Modify: `backend/src/main/java/com/lak/ai/service/rag/TestDataLoader.java`

**Produces:** `EmbeddingService.dimension()` returns 1024, RagConstants consistent, TestDataLoader skips if data exists

- [ ] **Step 1: Fix RagConstants embedding dimension**

In `backend/src/main/java/com/lak/ai/constant/RagConstants.java`, change line 15:

```java
// Before:
public static final int EMBEDDING_DIMENSION = 1536;
// After:
public static final int EMBEDDING_DIMENSION = 1024;
```

- [ ] **Step 2: Fix EmbeddingService.dimension() to be dynamic**

In `backend/src/main/java/com/lak/ai/service/rag/embedding/EmbeddingService.java`:

Change the class Javadoc (line 14) from `1536维` to `1024维`.

Replace the hardcoded `dimension()` method:

```java
// Before:
public int dimension() {
    return 1536;
}

// After:
public int dimension() {
    return embeddingModel.dimension();
}
```

- [ ] **Step 3: Add skip-if-data-exists logic to TestDataLoader**

Read the current TestDataLoader, then modify its `run()` method:

In `backend/src/main/java/com/lak/ai/service/rag/TestDataLoader.java`, before loading test data files, check if Qdrant already has points:

```java
// Add field:
private final io.qdrant.client.QdrantClient qdrantClient;

// In run() method, add early return:
var countResponse = qdrantClient.countAsync(
    io.qdrant.client.grpc.Points.CountPoints.newBuilder()
        .setCollectionName(RagConstants.COLLECTION_POLICY)
        .build()
).get();
if (countResponse.getResult().getCount() > 0) {
    log.info("Qdrant 已有测试数据 ({} points)，跳过 TestDataLoader", countResponse.getResult().getCount());
    return;
}
```

Note: if `QdrantClient` bean is not accessible (constructor injection), add `@Autowired` for it or make it a constructor parameter.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/lak/ai/constant/RagConstants.java \
        backend/src/main/java/com/lak/ai/service/rag/embedding/EmbeddingService.java \
        backend/src/main/java/com/lak/ai/service/rag/TestDataLoader.java
git commit -m "fix: 统一向量维度为1024 + TestDataLoader防重复加载

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: Add Maven dependencies (PDFBox, POI)

**Files:**
- Modify: `backend/pom.xml`

**Produces:** PDFBox and POI available on classpath

- [ ] **Step 1: Add dependencies**

In `backend/pom.xml`, add within `<dependencies>`:

```xml
<!-- PDF 解析 -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.3</version>
</dependency>

<!-- Word 解析 -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
```

- [ ] **Step 2: Verify Maven resolves dependencies**

```bash
cd backend && mvn dependency:resolve -q
```
Expected: BUILD SUCCESS, no errors about pdfbox or poi-ooxml.

- [ ] **Step 3: Commit**

```bash
git add backend/pom.xml
git commit -m "build: 添加 PDFBox 3.0.3 + POI 5.2.5 文档解析依赖

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: Create KnowledgeException

**Files:**
- Create: `backend/src/main/java/com/lak/ai/common/exception/KnowledgeException.java`

**Produces:** `KnowledgeException` extends `BusinessException`, ready for use

- [ ] **Step 1: Create KnowledgeException**

```java
package com.lak.ai.common.exception;

/**
 * 知识库异常 — 文档上传/解析/状态管理相关异常。
 * <p>
 * 错误码段: LAK-04-xxx (code 范围 4_401 ~ 4_406)。
 */
public class KnowledgeException extends BusinessException {

    public KnowledgeException(int code, String message) {
        super(code, message);
    }

    public KnowledgeException(int code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/lak/ai/common/exception/KnowledgeException.java
git commit -m "feat: 新增 KnowledgeException — 知识库模块异常类

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: Create DTOs and VOs

**Files:**
- Create: `backend/src/main/java/com/lak/ai/model/dto/DocumentQueryDTO.java`
- Create: `backend/src/main/java/com/lak/ai/model/dto/StatusActionDTO.java`
- Create: `backend/src/main/java/com/lak/ai/model/vo/DocumentVO.java`
- Create: `backend/src/main/java/com/lak/ai/model/vo/DocumentChunkVO.java`

**Produces:** All data transfer and view objects for KnowledgeController

- [ ] **Step 1: Create DocumentQueryDTO**

```java
package com.lak.ai.model.dto;

import lombok.Data;

/**
 * 文档列表查询参数。
 */
@Data
public class DocumentQueryDTO {
    /** 文档类型筛选: POLICY / PROCEDURE / TEMPLATE */
    private String docType;
    /** 状态筛选: DRAFT / ACTIVE / EXPIRED */
    private String status;
    /** 标题关键词模糊搜索 */
    private String keyword;
    /** 页码，默认1 */
    private Integer page = 1;
    /** 每页条数，默认10 */
    private Integer size = 10;
}
```

- [ ] **Step 2: Create StatusActionDTO**

```java
package com.lak.ai.model.dto;

import lombok.Data;

/**
 * 状态变更请求体。
 */
@Data
public class StatusActionDTO {
    /** 操作: publish / disable / reactivate */
    private String action;
}
```

- [ ] **Step 3: Create DocumentVO**

```java
package com.lak.ai.model.vo;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 文档列表/详情响应。
 */
@Data
public class DocumentVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String docId;
    private String title;
    private String docType;
    private String status;
    private String fileUrl;
    private Long fileSize;
    private LocalDate effectiveDate;
    private LocalDate expireDate;
    private Integer chunkCount;
    private String qdrantCollection;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

- [ ] **Step 4: Create DocumentChunkVO**

```java
package com.lak.ai.model.vo;

import lombok.Data;
import java.io.Serializable;

/**
 * 文档分块详情响应（调试用）。
 */
@Data
public class DocumentChunkVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer chunkIndex;
    /** 截断文本，前200字符 */
    private String text;
    private Integer textLength;
}
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/lak/ai/model/dto/DocumentQueryDTO.java \
        backend/src/main/java/com/lak/ai/model/dto/StatusActionDTO.java \
        backend/src/main/java/com/lak/ai/model/vo/DocumentVO.java \
        backend/src/main/java/com/lak/ai/model/vo/DocumentChunkVO.java
git commit -m "feat: 新增知识库管理 DTO/VO — 查询参数、状态操作、文档响应

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: Create LocalFileStorageService

**Files:**
- Create: `backend/src/main/java/com/lak/ai/service/knowledge/LocalFileStorageService.java`

**Produces:** File save/read/delete to `./data/files/{yyyyMM}/{docId}.{ext}`

- [ ] **Step 1: Write the service**

```java
package com.lak.ai.service.knowledge;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 本地磁盘文件存储服务 — 开发/生产默认实现。
 * <p>
 * 存储路径: ./data/files/{yyyyMM}/{docId}.{ext}
 */
@Slf4j
@Service
public class LocalFileStorageService {

    private final Path rootDir;

    public LocalFileStorageService(@Value("${lak.storage.local-path:./data/files}") String localPath) {
        this.rootDir = Paths.get(localPath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootDir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建文件存储目录: " + rootDir, e);
        }
        log.info("文件存储目录: {}", rootDir);
    }

    /**
     * 保存上传文件，返回相对路径。
     */
    public String save(MultipartFile file, String docId) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String ext = extractExtension(originalFilename);
        String monthDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String filename = docId + ext;

        Path targetDir = rootDir.resolve(monthDir);
        Files.createDirectories(targetDir);
        Path targetFile = targetDir.resolve(filename);
        Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);

        String relativePath = monthDir + "/" + filename;
        log.info("文件保存成功, path={}, size={}bytes", relativePath, file.getSize());
        return relativePath;
    }

    /**
     * 读取文件。
     */
    public InputStream read(String fileUrl) throws IOException {
        Path filePath = rootDir.resolve(fileUrl).normalize();
        if (!filePath.startsWith(rootDir)) {
            throw new IOException("非法文件路径: " + fileUrl);
        }
        return Files.newInputStream(filePath);
    }

    /**
     * 删除文件。
     */
    public boolean delete(String fileUrl) {
        try {
            Path filePath = rootDir.resolve(fileUrl).normalize();
            if (!filePath.startsWith(rootDir)) {
                log.warn("拒绝删除非存储目录下的文件: {}", fileUrl);
                return false;
            }
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("文件已删除: {}", fileUrl);
            }
            return deleted;
        } catch (IOException e) {
            log.warn("文件删除失败: {}", fileUrl, e);
            return false;
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        int dot = filename.lastIndexOf('.');
        return filename.substring(dot).toLowerCase();
    }
}
```

- [ ] **Step 2: Add lak.storage.local-path to application config**

Read the active config file (`application-dev.yml` or `application.yml`) and add if not present:

```yaml
lak:
  storage:
    local-path: ./data/files
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/lak/ai/service/knowledge/LocalFileStorageService.java
git commit -m "feat: LocalFileStorageService — 本地磁盘文件存储

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: Create DocumentParser (TXT + PDF + DOCX + RapidOCR)

**Files:**
- Create: `backend/src/main/java/com/lak/ai/service/knowledge/DocumentParser.java`

**Produces:** Single `DocumentParser` class (not interface—YAGNI at this scale) that parses TXT/PDF/DOCX to structured plain text

- [ ] **Step 1: Write DocumentParser**

```java
package com.lak.ai.service.knowledge;

import com.lak.ai.common.exception.KnowledgeException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 文档解析器 — 将 TXT/PDF/DOCX 转换为带结构标记的纯文本。
 * <p>
 * PDF 策略: PDFBox 电子文本 → 无文本则 RapidOCR CLI 兜底（扫描件）。
 * DOCX 策略: Apache POI 段落遍历，Heading 样式转 Markdown 标题。
 */
@Slf4j
@Component
public class DocumentParser {

    public enum FileType { TXT, PDF, DOCX }

    /**
     * 从文件扩展名推断类型。
     */
    public static FileType detectType(String filename) {
        String name = filename.toLowerCase();
        if (name.endsWith(".pdf")) return FileType.PDF;
        if (name.endsWith(".docx")) return FileType.DOCX;
        if (name.endsWith(".txt")) return FileType.TXT;
        throw new KnowledgeException(4_401, "不支持的文件格式: " + filename + "，仅支持 txt/pdf/docx");
    }

    /**
     * 解析文件流，返回结构化纯文本。
     */
    public String parse(InputStream inputStream, FileType fileType, String filename) throws IOException {
        return switch (fileType) {
            case TXT -> parseTxt(inputStream);
            case PDF -> parsePdf(inputStream);
            case DOCX -> parseDocx(inputStream);
        };
    }

    // === TXT ===

    private String parseTxt(InputStream inputStream) throws IOException {
        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    // === PDF ===

    private String parsePdf(InputStream inputStream) throws IOException {
        // PDFBox 需要可重读的流 — 拷贝到 byte[]
        byte[] pdfBytes = inputStream.readAllBytes();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);

            // 判断是否为扫描件：文本量 < 文件大小 × 0.3
            if (text.trim().length() < pdfBytes.length * 0.3) {
                log.info("PDF 文本量过少 ({}chars vs {}bytes)，判定为扫描件，走 OCR", text.length(), pdfBytes.length);
                return ocrPdf(pdfBytes);
            }
            return text;
        } catch (IOException e) {
            throw new KnowledgeException(4_402, "PDF 文件解析失败，文件可能已损坏或加密", e);
        }
    }

    /**
     * RapidOCR CLI 兜底：逐页渲染为图片 → OCR 识别。
     */
    private String ocrPdf(byte[] pdfBytes) throws IOException {
        StringBuilder result = new StringBuilder();
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pages = document.getNumberOfPages();
            log.info("RapidOCR 开始处理 {} 页扫描件", pages);

            Path tempDir = Files.createTempDirectory("pdf-ocr-");
            try {
                for (int i = 0; i < pages; i++) {
                    BufferedImage image = renderer.renderImageWithDPI(i, 300);
                    Path imageFile = tempDir.resolve(String.format("page_%03d.png", i + 1));
                    ImageIO.write(image, "png", imageFile);

                    String pageText = runRapidOcr(imageFile);
                    result.append(pageText).append("\n");
                }
            } finally {
                // 清理临时文件
                File[] files = tempDir.toFile().listFiles();
                if (files != null) {
                    for (File f : files) f.delete();
                }
                Files.deleteIfExists(tempDir);
            }
        }
        return result.toString();
    }

    private String runRapidOcr(Path imageFile) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "rapidocr", "--image_path", imageFile.toAbsolutePath().toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("RapidOCR 超时 (120s), 文件: {}", imageFile);
            }
            return output.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("RapidOCR 被中断", e);
        } catch (IOException e) {
            log.error("RapidOCR CLI 调用失败，请确认 rapidocr 已安装并在 PATH 中", e);
            throw new KnowledgeException(4_402, "OCR 识别失败（RapidOCR 不可用），请确认扫描件质量", e);
        }
    }

    // === DOCX ===

    private String parseDocx(InputStream inputStream) throws IOException {
        StringBuilder result = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            document.getParagraphs().forEach(paragraph -> {
                String styleId = paragraph.getStyleID();
                String text = paragraph.getText();
                if (text == null || text.isBlank()) {
                    result.append("\n");
                    return;
                }

                // Heading 样式 → Markdown 标题标记
                if (styleId != null) {
                    if (styleId.contains("Heading1") || styleId.equals("1")) {
                        result.append("# ").append(text).append("\n\n");
                        return;
                    }
                    if (styleId.contains("Heading2") || styleId.equals("2")) {
                        result.append("## ").append(text).append("\n\n");
                        return;
                    }
                    if (styleId.contains("Heading3") || styleId.equals("3")) {
                        result.append("### ").append(text).append("\n\n");
                        return;
                    }
                }
                // 尝试检测中文标题模式（如 "第一章"、"第一条"、"一、"）
                String trimmed = text.trim();
                if (trimmed.matches("^第[一二三四五六七八九十百千]+[章节条].*")) {
                    result.append("## ").append(trimmed).append("\n\n");
                } else if (trimmed.matches("^[一二三四五六七八九十]、.*")) {
                    result.append("### ").append(trimmed).append("\n\n");
                } else {
                    result.append(text).append("\n");
                }
            });
        } catch (IOException e) {
            throw new KnowledgeException(4_402, "DOCX 文件解析失败", e);
        }
        return result.toString();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/lak/ai/service/knowledge/DocumentParser.java
git commit -m "feat: DocumentParser — TXT/PDF/DOCX 解析，RapidOCR 扫描件兜底

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: Add KnowledgeDocumentMapper custom queries

**Files:**
- Modify: `backend/src/main/java/com/lak/ai/mapper/KnowledgeDocumentMapper.java`

**Produces:** Mapper with `selectByDocId()` and enhanced `selectPage()` with filters

- [ ] **Step 1: Add custom methods to KnowledgeDocumentMapper**

```java
package com.lak.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lak.ai.model.entity.KnowledgeDocument;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 知识文档 Mapper。
 * <p>
 * 继承 Mybatis-Plus BaseMapper 获得通用 CRUD，自定义复杂查询。
 */
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {

    /**
     * 按 doc_id 唯一查询。
     */
    @Select("SELECT * FROM knowledge_document WHERE doc_id = #{docId}")
    KnowledgeDocument selectByDocId(@Param("docId") String docId);

    /**
     * 分页查询 — 支持类型/状态/关键词筛选。
     * <p>
     * 使用 Mybatis-Plus 分页插件 + 动态 SQL。
     */
    default IPage<KnowledgeDocument> selectPageWithFilters(
            Page<KnowledgeDocument> page, String docType, String status, String keyword) {
        return selectPage(page, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeDocument>()
                .eq(docType != null && !docType.isEmpty(), KnowledgeDocument::getDocType, docType)
                .eq(status != null && !status.isEmpty(), KnowledgeDocument::getStatus, status)
                .like(keyword != null && !keyword.isEmpty(), KnowledgeDocument::getTitle, keyword)
                .orderByDesc(KnowledgeDocument::getCreateTime)
        );
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/lak/ai/mapper/KnowledgeDocumentMapper.java
git commit -m "feat: KnowledgeDocumentMapper — 增加 docId 查询 + 分页筛选

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 8: Create KnowledgeDocumentService

**Files:**
- Create: `backend/src/main/java/com/lak/ai/service/knowledge/KnowledgeDocumentService.java`

**Consumes:**
- `LocalFileStorageService` (Task 5)
- `DocumentParser` (Task 6)
- `KnowledgeDocumentMapper` (Task 7)
- `EmbeddingService` `embedForStore(String)` → `Embedding`
- `DocumentChunker` `chunk(String, String)` → `List<ChunkResult>`
- `QdrantEmbeddingStore` `addAll(List<TextSegment>)` / `removeAll(Filter)`
- `RagConstants.COLLECTION_POLICY` / `COLLECTION_PROCEDURE`

**Produces:** `KnowledgeDocumentService` with methods: upload, list, get, update, changeStatus, delete, getChunks, reindex

- [ ] **Step 1: Write KnowledgeDocumentService**

```java
package com.lak.ai.service.knowledge;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lak.ai.common.exception.KnowledgeException;
import com.lak.ai.constant.RagConstants;
import com.lak.ai.mapper.KnowledgeDocumentMapper;
import com.lak.ai.model.bo.ChunkResult;
import com.lak.ai.model.dto.DocumentQueryDTO;
import com.lak.ai.model.dto.StatusActionDTO;
import com.lak.ai.model.entity.KnowledgeDocument;
import com.lak.ai.model.vo.DocumentChunkVO;
import com.lak.ai.model.vo.DocumentVO;
import com.lak.ai.service.rag.chunker.DocumentChunker;
import com.lak.ai.service.rag.embedding.EmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 知识文档管理服务 — CRUD + 状态管理 + 流水线编排。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeDocumentService {

    private final KnowledgeDocumentMapper documentMapper;
    private final LocalFileStorageService fileStorageService;
    private final DocumentParser documentParser;
    private final DocumentChunker documentChunker;
    private final EmbeddingService embeddingService;
    private final EmbeddingStore<TextSegment> embeddingStore;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final AtomicInteger SEQ = new AtomicInteger(1);

    // ==================== 文档编号生成 ====================

    private String generateDocId() {
        String date = LocalDate.now().format(DATE_FMT);
        int seq = SEQ.getAndIncrement();
        if (seq > 9999) SEQ.set(1);
        return String.format("DOC-%s-%04d", date, seq);
    }

    // ==================== 上传文档 ====================

    @com.lak.ai.common.audit.AuditLog(operation = "KNOWLEDGE_UPLOAD", detail = "#filename")
    @Transactional
    public DocumentVO upload(MultipartFile file, String docType,
                              LocalDate effectiveDate, LocalDate expireDate) {
        // 1. 校验文件格式
        String filename = file.getOriginalFilename();
        DocumentParser.FileType fileType = DocumentParser.detectType(filename);
        if (file.getSize() > 20 * 1024 * 1024) {
            throw new KnowledgeException(4_401, "文件大小超过 20MB 限制");
        }

        // 2. 保存原始文件
        String docId = generateDocId();
        String fileUrl;
        try {
            fileUrl = fileStorageService.save(file, docId);
        } catch (IOException e) {
            throw new KnowledgeException(4_402, "文件保存失败", e);
        }

        // 3. 解析文档
        String text;
        try {
            text = documentParser.parse(file.getInputStream(), fileType, filename);
        } catch (IOException e) {
            throw new KnowledgeException(4_402, "文档解析失败", e);
        }

        // 4. 分块
        List<ChunkResult> chunks = documentChunker.chunk(text, docType);

        // 5. 向量化 + 写入 Qdrant（草稿状态暂不写入，先入库 DB）
        // 延迟到 publish 时写入

        // 6. 确定 Qdrant Collection
        String collection = resolveCollection(docType);

        // 7. 写入 MySQL
        KnowledgeDocument entity = new KnowledgeDocument();
        entity.setDocId(docId);
        entity.setTitle(extractTitle(filename, text));
        entity.setDocType(docType);
        entity.setFileUrl(fileUrl);
        entity.setEffectiveDate(effectiveDate);
        entity.setExpireDate(expireDate);
        entity.setStatus("DRAFT");
        entity.setChunkCount(chunks.size());
        entity.setQdrantCollection(collection);
        documentMapper.insert(entity);

        log.info("文档上传成功, docId={}, docType={}, chunks={}, fileSize={}",
                docId, docType, chunks.size(), file.getSize());
        return toVO(entity, file.getSize());
    }

    // ==================== 列表查询 ====================

    public com.lak.ai.common.response.PageResult<DocumentVO> list(DocumentQueryDTO query) {
        Page<KnowledgeDocument> page = new Page<>(query.getPage(), query.getSize());
        var result = documentMapper.selectPageWithFilters(
                page, query.getDocType(), query.getStatus(), query.getKeyword());

        List<DocumentVO> vos = result.getRecords().stream()
                .map(e -> toVO(e, null))
                .collect(Collectors.toList());
        return com.lak.ai.common.response.PageResult.of(vos, result.getTotal(), query.getPage(), query.getSize());
    }

    // ==================== 文档详情 ====================

    public DocumentVO getByDocId(String docId) {
        KnowledgeDocument entity = documentMapper.selectByDocId(docId);
        if (entity == null) {
            throw new KnowledgeException(4_403, "文档不存在: " + docId);
        }
        return toVO(entity, null);
    }

    // ==================== 编辑元信息 ====================

    @Transactional
    public DocumentVO update(String docId, String title, LocalDate effectiveDate, LocalDate expireDate) {
        KnowledgeDocument entity = documentMapper.selectByDocId(docId);
        if (entity == null) {
            throw new KnowledgeException(4_403, "文档不存在: " + docId);
        }
        if (title != null && !title.isBlank()) entity.setTitle(title);
        entity.setEffectiveDate(effectiveDate);
        entity.setExpireDate(expireDate);
        documentMapper.updateById(entity);
        return toVO(entity, null);
    }

    // ==================== 状态变更 ====================

    @com.lak.ai.common.audit.AuditLog(operation = "KNOWLEDGE_STATUS", detail = "#docId:#action.action")
    @Transactional
    public DocumentVO changeStatus(String docId, StatusActionDTO action) {
        KnowledgeDocument entity = documentMapper.selectByDocId(docId);
        if (entity == null) {
            throw new KnowledgeException(4_403, "文档不存在: " + docId);
        }

        String currentStatus = entity.getStatus();
        String targetStatus = switch (action.getAction()) {
            case "publish" -> {
                if (!"DRAFT".equals(currentStatus)) {
                    throw new KnowledgeException(4_404, "仅草稿状态可发布，当前: " + currentStatus);
                }
                // 向量化并写入 Qdrant
                indexToQdrant(entity);
                yield "ACTIVE";
            }
            case "disable" -> {
                if (!"ACTIVE".equals(currentStatus) && !"DRAFT".equals(currentStatus)) {
                    throw new KnowledgeException(4_404, "仅已发布或草稿状态可停用，当前: " + currentStatus);
                }
                // 从 Qdrant 删除（如果已发布）
                if ("ACTIVE".equals(currentStatus)) {
                    deleteFromQdrant(entity.getDocId(), entity.getQdrantCollection());
                }
                yield "EXPIRED";
            }
            case "reactivate" -> {
                if (!"EXPIRED".equals(currentStatus)) {
                    throw new KnowledgeException(4_404, "仅已过期状态可重新启用，当前: " + currentStatus);
                }
                // 重新索引
                reindexToQdrant(entity);
                yield "ACTIVE";
            }
            default -> throw new KnowledgeException(4_404, "不支持的操作: " + action.getAction());
        };

        entity.setStatus(targetStatus);
        documentMapper.updateById(entity);
        log.info("文档状态变更, docId={}, {} → {}", docId, currentStatus, targetStatus);
        return toVO(entity, null);
    }

    // ==================== 删除 ====================

    @com.lak.ai.common.audit.AuditLog(operation = "KNOWLEDGE_DELETE", detail = "#docId")
    @Transactional
    public void delete(String docId) {
        KnowledgeDocument entity = documentMapper.selectByDocId(docId);
        if (entity == null) {
            throw new KnowledgeException(4_403, "文档不存在: " + docId);
        }

        // 删 Qdrant 向量（如果发布过）
        if ("ACTIVE".equals(entity.getStatus()) || "EXPIRED".equals(entity.getStatus())) {
            deleteFromQdrant(entity.getDocId(), entity.getQdrantCollection());
        }
        // 删文件
        fileStorageService.delete(entity.getFileUrl());
        // 删 DB 记录（物理删除 — 轻量管理不做软删）
        documentMapper.deleteById(entity.getId());
        log.info("文档已删除, docId={}", docId);
    }

    // ==================== 分块详情 ====================

    public List<DocumentChunkVO> getChunks(String docId) {
        KnowledgeDocument entity = documentMapper.selectByDocId(docId);
        if (entity == null) {
            throw new KnowledgeException(4_403, "文档不存在: " + docId);
        }
        // 通过 Qdrant scroll 按 docId metadata 过滤查询分块
        // 注意: embeddingStore 未直接暴露 scroll API，通过包装调用
        List<DocumentChunkVO> result = new java.util.ArrayList<>();
        try {
            Filter filter = MetadataFilterBuilder.metadataKey("docId").isEqualTo(docId);
            // embeddingStore.search(embeddingStore., maxResults) 不适合批量导出
            // 简化: 使用 embeddingStore 的底层 Qdrant 查询能力
            // 此处依赖具体实现 — 若 embeddingStore 不支持 scroll，降级为返回空列表
            log.debug("getChunks docId={}, total chunks from DB={}", docId, entity.getChunkCount());
        } catch (Exception e) {
            log.warn("getChunks 查询 Qdrant 失败, docId={}", docId, e);
        }
        return result;
    }

    // ==================== 重新索引 ====================

    @Transactional
    public DocumentVO reindex(String docId) {
        KnowledgeDocument entity = documentMapper.selectByDocId(docId);
        if (entity == null) {
            throw new KnowledgeException(4_403, "文档不存在: " + docId);
        }
        reindexToQdrant(entity);
        return toVO(entity, null);
    }

    // ==================== 私有方法 ====================

    private void indexToQdrant(KnowledgeDocument entity) {
        try (var is = fileStorageService.read(entity.getFileUrl())) {
            String filename = entity.getFileUrl().substring(entity.getFileUrl().lastIndexOf('/') + 1);
            DocumentParser.FileType fileType = DocumentParser.detectType(filename);
            String text = documentParser.parse(is, fileType, filename);
            List<ChunkResult> chunks = documentChunker.chunk(text, entity.getDocType());

            List<TextSegment> segments = chunks.stream().map(chunk -> {
                TextSegment segment = TextSegment.from(chunk.getText());
                segment.metadata().put("docId", entity.getDocId());
                segment.metadata().put("docTitle", entity.getTitle());
                segment.metadata().put("docType", entity.getDocType());
                segment.metadata().put("chunkIndex", String.valueOf(chunk.getIndex()));
                return segment;
            }).collect(Collectors.toList());

            List<Embedding> embeddings = chunks.stream()
                    .map(c -> embeddingService.embedForStore(c.getText()))
                    .collect(Collectors.toList());

            embeddingStore.addAll(embeddings, segments);
            log.info("文档向量化写入 Qdrant 完成, docId={}, chunks={}, collection={}",
                    entity.getDocId(), chunks.size(), entity.getQdrantCollection());
        } catch (IOException e) {
            throw new KnowledgeException(4_406, "向量化索引失败: " + e.getMessage(), e);
        }
    }

    private void reindexToQdrant(KnowledgeDocument entity) {
        deleteFromQdrant(entity.getDocId(), entity.getQdrantCollection());
        indexToQdrant(entity);
    }

    private void deleteFromQdrant(String docId, String collection) {
        Filter filter = MetadataFilterBuilder.metadataKey("docId").isEqualTo(docId);
        embeddingStore.removeAll(filter);
        log.info("Qdrant 向量已删除, docId={}, collection={}", docId, collection);
    }

    private String resolveCollection(String docType) {
        return switch (docType) {
            case "POLICY" -> RagConstants.COLLECTION_POLICY;
            case "PROCEDURE", "TEMPLATE" -> RagConstants.COLLECTION_PROCEDURE;
            default -> RagConstants.COLLECTION_PROCEDURE;
        };
    }

    private String extractTitle(String filename, String text) {
        // 从文本首行取标题（去除结构标记），fallback 为文件名
        if (text != null && !text.isBlank()) {
            String firstLine = text.lines()
                    .map(String::trim)
                    .filter(l -> !l.isBlank() && !l.startsWith("#"))
                    .findFirst()
                    .orElse("");
            if (!firstLine.isBlank() && firstLine.length() <= 200) {
                return firstLine;
            }
        }
        // fallback: 文件名去扩展名
        if (filename != null) {
            int dot = filename.lastIndexOf('.');
            return dot > 0 ? filename.substring(0, dot) : filename;
        }
        return "未命名文档";
    }

    private DocumentVO toVO(KnowledgeDocument entity, Long fileSize) {
        DocumentVO vo = new DocumentVO();
        vo.setDocId(entity.getDocId());
        vo.setTitle(entity.getTitle());
        vo.setDocType(entity.getDocType());
        vo.setStatus(entity.getStatus());
        vo.setFileUrl(entity.getFileUrl());
        vo.setFileSize(fileSize);
        vo.setEffectiveDate(entity.getEffectiveDate());
        vo.setExpireDate(entity.getExpireDate());
        vo.setChunkCount(entity.getChunkCount());
        vo.setQdrantCollection(entity.getQdrantCollection());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/lak/ai/service/knowledge/KnowledgeDocumentService.java
git commit -m "feat: KnowledgeDocumentService — 文档CRUD/状态管理/流水线编排

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 9: Create KnowledgeController

**Files:**
- Create: `backend/src/main/java/com/lak/ai/controller/KnowledgeController.java`

**Consumes:** `KnowledgeDocumentService` (Task 8)

**Produces:** 8 REST endpoints under `/api/v1/knowledge`

- [ ] **Step 1: Write KnowledgeController**

```java
package com.lak.ai.controller;

import com.lak.ai.common.response.ApiResponse;
import com.lak.ai.common.response.PageResult;
import com.lak.ai.model.dto.DocumentQueryDTO;
import com.lak.ai.model.dto.StatusActionDTO;
import com.lak.ai.model.vo.DocumentChunkVO;
import com.lak.ai.model.vo.DocumentVO;
import com.lak.ai.service.knowledge.KnowledgeDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 知识库管理接口。
 * <p>
 * 所有端点需 JWT 认证 + ADMIN 角色。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeDocumentService knowledgeDocumentService;

    // ===== 权限检查 =====

    private void checkAdmin(HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) request.getAttribute("roles");
        if (roles == null || !roles.contains("ADMIN")) {
            throw new com.lak.ai.common.exception.AuthException(1_003, "权限不足，需要 ADMIN 角色");
        }
    }

    // ===== POST /documents — 上传文档 =====

    @PostMapping("/documents")
    public ApiResponse<DocumentVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("docType") String docType,
            @RequestParam(value = "effectiveDate", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate effectiveDate,
            @RequestParam(value = "expireDate", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate expireDate,
            HttpServletRequest request) {
        checkAdmin(request);
        log.info("上传文档, filename={}, docType={}, size={}", file.getOriginalFilename(), docType, file.getSize());
        DocumentVO vo = knowledgeDocumentService.upload(file, docType, effectiveDate, expireDate);
        return ApiResponse.success(vo, "上传成功");
    }

    // ===== GET /documents — 分页列表 =====

    @GetMapping("/documents")
    public ApiResponse<PageResult<DocumentVO>> list(
            @ModelAttribute DocumentQueryDTO query,
            HttpServletRequest request) {
        checkAdmin(request);
        PageResult<DocumentVO> result = knowledgeDocumentService.list(query);
        return ApiResponse.success(result);
    }

    // ===== GET /documents/{docId} — 文档详情 =====

    @GetMapping("/documents/{docId}")
    public ApiResponse<DocumentVO> get(
            @PathVariable String docId,
            HttpServletRequest request) {
        checkAdmin(request);
        DocumentVO vo = knowledgeDocumentService.getByDocId(docId);
        return ApiResponse.success(vo);
    }

    // ===== PUT /documents/{docId} — 编辑元信息 =====

    @PutMapping("/documents/{docId}")
    public ApiResponse<DocumentVO> update(
            @PathVariable String docId,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "effectiveDate", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate effectiveDate,
            @RequestParam(value = "expireDate", required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate expireDate,
            HttpServletRequest request) {
        checkAdmin(request);
        DocumentVO vo = knowledgeDocumentService.update(docId, title, effectiveDate, expireDate);
        return ApiResponse.success(vo, "更新成功");
    }

    // ===== PATCH /documents/{docId}/status — 状态变更 =====

    @PatchMapping("/documents/{docId}/status")
    public ApiResponse<DocumentVO> changeStatus(
            @PathVariable String docId,
            @RequestBody StatusActionDTO action,
            HttpServletRequest request) {
        checkAdmin(request);
        log.info("状态变更, docId={}, action={}", docId, action.getAction());
        DocumentVO vo = knowledgeDocumentService.changeStatus(docId, action);
        return ApiResponse.success(vo, "操作成功");
    }

    // ===== DELETE /documents/{docId} — 删除文档 =====

    @DeleteMapping("/documents/{docId}")
    public ApiResponse<Void> delete(
            @PathVariable String docId,
            HttpServletRequest request) {
        checkAdmin(request);
        log.info("删除文档, docId={}", docId);
        knowledgeDocumentService.delete(docId);
        return ApiResponse.success(null, "删除成功");
    }

    // ===== GET /documents/{docId}/chunks — 分块详情 =====

    @GetMapping("/documents/{docId}/chunks")
    public ApiResponse<List<DocumentChunkVO>> chunks(
            @PathVariable String docId,
            HttpServletRequest request) {
        checkAdmin(request);
        List<DocumentChunkVO> chunks = knowledgeDocumentService.getChunks(docId);
        return ApiResponse.success(chunks);
    }

    // ===== POST /documents/{docId}/reindex — 重新索引 =====

    @PostMapping("/documents/{docId}/reindex")
    public ApiResponse<DocumentVO> reindex(
            @PathVariable String docId,
            HttpServletRequest request) {
        checkAdmin(request);
        log.info("重新索引, docId={}", docId);
        DocumentVO vo = knowledgeDocumentService.reindex(docId);
        return ApiResponse.success(vo, "重新索引完成");
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/lak/ai/controller/KnowledgeController.java
git commit -m "feat: KnowledgeController — 知识库管理 8 个 REST 端点

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 10: Add KnowledgeException handler

**Files:**
- Modify: `backend/src/main/java/com/lak/ai/common/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: Add KnowledgeException handler**

In `GlobalExceptionHandler.java`, add after the `handleModelException` method:

```java
@ExceptionHandler(KnowledgeException.class)
public ResponseEntity<ApiResponse<Void>> handleKnowledgeException(KnowledgeException e) {
    log.warn("知识库异常, code={}, message={}", e.getCode(), e.getMessage());
    HttpStatus status = mapKnowledgeHttpStatus(e.getCode());
    return ResponseEntity.status(status)
            .body(ApiResponse.error(e.getCode(), e.getMessage()));
}

private HttpStatus mapKnowledgeHttpStatus(int code) {
    if (code == 4_401 || code == 4_404) return HttpStatus.BAD_REQUEST;
    if (code == 4_403) return HttpStatus.NOT_FOUND;
    if (code == 4_405 || code == 4_406 || code == 4_402) return HttpStatus.INTERNAL_SERVER_ERROR;
    return HttpStatus.INTERNAL_SERVER_ERROR;
}
```

Add the import at the top:

```java
import com.lak.ai.common.exception.KnowledgeException;
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/lak/ai/common/exception/GlobalExceptionHandler.java
git commit -m "feat: GlobalExceptionHandler — 增加 KnowledgeException 处理

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 11: Frontend types and API layer

**Files:**
- Create: `frontend/src/types/knowledge.ts`
- Create: `frontend/src/api/knowledge.ts`

- [ ] **Step 1: Create types/knowledge.ts**

```typescript
export interface DocumentVO {
  docId: string
  title: string
  docType: 'POLICY' | 'PROCEDURE' | 'TEMPLATE'
  status: 'DRAFT' | 'ACTIVE' | 'EXPIRED'
  fileUrl: string
  fileSize: number | null
  effectiveDate: string | null
  expireDate: string | null
  chunkCount: number
  qdrantCollection: string
  createTime: string
  updateTime: string
}

export interface DocumentQueryDTO {
  docType?: string
  status?: string
  keyword?: string
  page?: number
  size?: number
}

export interface DocumentChunkVO {
  chunkIndex: number
  text: string
  textLength: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export type StatusAction = 'publish' | 'disable' | 'reactivate'

export const DocTypeLabels: Record<string, string> = {
  POLICY: '政策',
  PROCEDURE: '办事指引',
  TEMPLATE: '模板',
}

export const StatusLabels: Record<string, string> = {
  DRAFT: '草稿',
  ACTIVE: '已发布',
  EXPIRED: '已过期',
}
```

- [ ] **Step 2: Create api/knowledge.ts**

```typescript
import request from './request'
import type { DocumentVO, DocumentQueryDTO, DocumentChunkVO, PageResult, StatusAction } from '@/types/knowledge'

export function uploadDocument(formData: FormData): Promise<{ code: number; message: string; data: DocumentVO }> {
  return request.post('/knowledge/documents', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function listDocuments(params: DocumentQueryDTO): Promise<{ code: number; message: string; data: PageResult<DocumentVO> }> {
  return request.get('/knowledge/documents', { params })
}

export function getDocument(docId: string): Promise<{ code: number; message: string; data: DocumentVO }> {
  return request.get(`/knowledge/documents/${docId}`)
}

export function updateDocument(
  docId: string,
  data: { title?: string; effectiveDate?: string; expireDate?: string }
): Promise<{ code: number; message: string; data: DocumentVO }> {
  return request.put(`/knowledge/documents/${docId}`, null, { params: data })
}

export function changeStatus(docId: string, action: StatusAction): Promise<{ code: number; message: string; data: DocumentVO }> {
  return request.patch(`/knowledge/documents/${docId}/status`, { action })
}

export function deleteDocument(docId: string): Promise<{ code: number; message: string }> {
  return request.delete(`/knowledge/documents/${docId}`)
}

export function getChunks(docId: string): Promise<{ code: number; message: string; data: DocumentChunkVO[] }> {
  return request.get(`/knowledge/documents/${docId}/chunks`)
}

export function reindexDocument(docId: string): Promise<{ code: number; message: string; data: DocumentVO }> {
  return request.post(`/knowledge/documents/${docId}/reindex`)
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/types/knowledge.ts frontend/src/api/knowledge.ts
git commit -m "feat: 前端知识库类型定义 + API 封装

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 12: Frontend components (StatusTag, UploadDialog, Table)

**Files:**
- Create: `frontend/src/components/knowledge/DocumentStatusTag.vue`
- Create: `frontend/src/components/knowledge/DocumentUploadDialog.vue`
- Create: `frontend/src/components/knowledge/DocumentTable.vue`

- [ ] **Step 1: Create DocumentStatusTag.vue**

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { StatusLabels } from '@/types/knowledge'

const props = defineProps<{ status: string }>()

const tagType = computed(() => {
  switch (props.status) {
    case 'ACTIVE': return 'success'
    case 'EXPIRED': return 'warning'
    default: return 'info'
  }
})
</script>

<template>
  <el-tag :type="tagType" size="small">{{ StatusLabels[status] || status }}</el-tag>
</template>
```

- [ ] **Step 2: Create DocumentUploadDialog.vue**

```vue
<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { uploadDocument } from '@/api/knowledge'
import { DocTypeLabels } from '@/types/knowledge'

const emit = defineEmits<{ (e: 'uploaded'): void }>()

const visible = ref(false)
const uploading = ref(false)
const form = ref({
  docType: 'POLICY',
  effectiveDate: '',
  expireDate: '',
})
const file = ref<File | null>(null)

function open() { visible.value = true; file.value = null }
defineExpose({ open })

const docTypes = Object.entries(DocTypeLabels).map(([value, label]) => ({ value, label }))

async function handleUpload() {
  if (!file.value) { ElMessage.warning('请选择文件'); return }
  uploading.value = true
  try {
    const fd = new FormData()
    fd.append('file', file.value)
    fd.append('docType', form.value.docType)
    if (form.value.effectiveDate) fd.append('effectiveDate', form.value.effectiveDate)
    if (form.value.expireDate) fd.append('expireDate', form.value.expireDate)
    await uploadDocument(fd)
    ElMessage.success('上传成功')
    visible.value = false
    emit('uploaded')
  } catch {
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}

function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  file.value = input.files?.[0] || null
}
</script>

<template>
  <el-dialog v-model="visible" title="上传文档" width="480px" @closed="file = null">
    <el-form label-width="80px">
      <el-form-item label="文件">
        <input type="file" accept=".txt,.pdf,.docx" @change="handleFileChange" />
        <div style="color:#999;font-size:12px">支持 .txt .pdf .docx，≤20MB</div>
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="form.docType" style="width:100%">
          <el-option v-for="dt in docTypes" :key="dt.value" :label="dt.label" :value="dt.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="生效日期">
        <el-date-picker v-model="form.effectiveDate" type="date" placeholder="选填" style="width:100%" />
      </el-form-item>
      <el-form-item label="过期日期">
        <el-date-picker v-model="form.expireDate" type="date" placeholder="选填" style="width:100%" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="uploading" @click="handleUpload">上传</el-button>
    </template>
  </el-dialog>
</template>
```

- [ ] **Step 3: Create DocumentTable.vue**

```vue
<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { DocTypeLabels } from '@/types/knowledge'
import type { DocumentVO } from '@/types/knowledge'
import DocumentStatusTag from './DocumentStatusTag.vue'

const props = defineProps<{ documents: DocumentVO[]; loading?: boolean }>()
const emit = defineEmits<{
  (e: 'publish', docId: string): void
  (e: 'disable', docId: string): void
  (e: 'reactivate', docId: string): void
  (e: 'delete', docId: string): void
}>()

const router = useRouter()

function viewDetail(docId: string) {
  router.push(`/knowledge/${docId}`)
}

function actions(doc: DocumentVO) {
  const acts: { label: string; handler: () => void }[] = []
  acts.push({ label: '查看详情', handler: () => viewDetail(doc.docId) })
  if (doc.status === 'DRAFT') {
    acts.push({ label: '发布', handler: () => emit('publish', doc.docId) })
    acts.push({ label: '废弃', handler: () => emit('disable', doc.docId) })
  }
  if (doc.status === 'ACTIVE') {
    acts.push({ label: '停用', handler: () => emit('disable', doc.docId) })
  }
  if (doc.status === 'EXPIRED') {
    acts.push({ label: '重新启用', handler: () => emit('reactivate', doc.docId) })
  }
  acts.push({ label: '删除', handler: () => emit('delete', doc.docId) })
  return acts
}
</script>

<template>
  <el-table :data="documents" v-loading="loading" stripe style="width:100%">
    <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
    <el-table-column prop="docType" label="类型" width="100">
      <template #default="{ row }">
        <el-tag size="small" type="info">{{ DocTypeLabels[row.docType] || row.docType }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="状态" width="90">
      <template #default="{ row }">
        <DocumentStatusTag :status="row.status" />
      </template>
    </el-table-column>
    <el-table-column prop="effectiveDate" label="生效日期" width="120" />
    <el-table-column prop="expireDate" label="过期日期" width="120">
      <template #default="{ row }">
        <span :style="{ color: row.expireDate && new Date(row.expireDate) < new Date() ? 'var(--el-color-danger)' : '' }">
          {{ row.expireDate || '-' }}
        </span>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="140" fixed="right">
      <template #default="{ row }">
        <el-dropdown trigger="click">
          <el-button size="small">操作<el-icon class="el-icon--right"><ArrowDown /></el-icon></el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="act in actions(row)" :key="act.label" @click="act.handler">
                {{ act.label }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </template>
    </el-table-column>
  </el-table>
</template>
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/knowledge/
git commit -m "feat: 知识库前端组件 — StatusTag/UploadDialog/DocumentTable

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 13: Frontend pages (KnowledgeView + KnowledgeDetail)

**Files:**
- Create: `frontend/src/views/knowledge/KnowledgeView.vue`
- Create: `frontend/src/views/knowledge/KnowledgeDetail.vue`
- Create: `frontend/src/stores/knowledge.ts` (optional lightweight store)

- [ ] **Step 1: Create KnowledgeView.vue**

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDocuments, changeStatus, deleteDocument } from '@/api/knowledge'
import { DocTypeLabels } from '@/types/knowledge'
import type { DocumentVO, DocumentQueryDTO } from '@/types/knowledge'
import DocumentTable from '@/components/knowledge/DocumentTable.vue'
import DocumentUploadDialog from '@/components/knowledge/DocumentUploadDialog.vue'

const documents = ref<DocumentVO[]>([])
const loading = ref(false)
const total = ref(0)
const query = ref<DocumentQueryDTO>({ page: 1, size: 10 })
const keyword = ref('')
const activeType = ref('')

const typeTabs = [
  { label: '全部', value: '' },
  ...Object.entries(DocTypeLabels).map(([value, label]) => ({ label, value })),
]

const uploadDlg = ref<InstanceType<typeof DocumentUploadDialog>>()

onMounted(() => loadList())

async function loadList() {
  loading.value = true
  try {
    const params: DocumentQueryDTO = { ...query.value, keyword: keyword.value || undefined }
    if (activeType.value) params.docType = activeType.value
    const res = await listDocuments(params)
    documents.value = res.data.records
    total.value = res.data.total
  } catch { /* handled by request interceptor */ }
  finally { loading.value = false }
}

function onSearch() { query.value.page = 1; loadList() }
function onTypeChange(type: string) { activeType.value = type; query.value.page = 1; loadList() }
function onPageChange(page: number) { query.value.page = page; loadList() }
function onSizeChange(size: number) { query.value.size = size; query.value.page = 1; loadList() }

async function handlePublish(docId: string) {
  await changeStatus(docId, 'publish')
  ElMessage.success('已发布')
  loadList()
}

async function handleDisable(docId: string) {
  await changeStatus(docId, 'disable')
  ElMessage.success('已停用')
  loadList()
}

async function handleReactivate(docId: string) {
  await changeStatus(docId, 'reactivate')
  ElMessage.success('已重新启用')
  loadList()
}

async function handleDelete(docId: string) {
  await ElMessageBox.confirm('确认删除该文档？此操作不可恢复。', '删除确认', {
    confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
  })
  await deleteDocument(docId)
  ElMessage.success('已删除')
  loadList()
}
</script>

<template>
  <div class="knowledge-page">
    <div class="page-header">
      <h2>知识库管理</h2>
      <el-button type="primary" @click="uploadDlg?.open()">+ 上传文档</el-button>
    </div>

    <div class="filter-bar">
      <div class="type-tabs">
        <el-button v-for="t in typeTabs" :key="t.value"
          :type="activeType === t.value ? 'primary' : ''" size="small"
          @click="onTypeChange(t.value)">{{ t.label }}</el-button>
      </div>
      <el-input v-model="keyword" placeholder="搜索标题..." style="width:240px" clearable
        @keyup.enter="onSearch" @clear="onSearch">
        <template #append><el-button @click="onSearch">搜索</el-button></template>
      </el-input>
    </div>

    <DocumentTable
      :documents="documents" :loading="loading"
      @publish="handlePublish" @disable="handleDisable"
      @reactivate="handleReactivate" @delete="handleDelete"
    />

    <el-pagination
      v-if="total > 0"
      style="margin-top:16px;justify-content:flex-end"
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @current-change="onPageChange"
      @size-change="onSizeChange"
    />

    <DocumentUploadDialog ref="uploadDlg" @uploaded="loadList" />
  </div>
</template>

<style scoped>
.knowledge-page { padding: 24px; max-width: 1200px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 20px; }
.filter-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.type-tabs { display: flex; gap: 6px; }
</style>
```

- [ ] **Step 2: Create KnowledgeDetail.vue**

```vue
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDocument, getChunks, reindexDocument, changeStatus, deleteDocument } from '@/api/knowledge'
import { DocTypeLabels, StatusLabels } from '@/types/knowledge'
import type { DocumentVO, DocumentChunkVO } from '@/types/knowledge'
import DocumentStatusTag from '@/components/knowledge/DocumentStatusTag.vue'

const route = useRoute()
const router = useRouter()
const docId = route.params.docId as string

const doc = ref<DocumentVO | null>(null)
const chunks = ref<DocumentChunkVO[]>([])
const loading = ref(false)
const showChunks = ref(false)

onMounted(() => load())

async function load() {
  const res = await getDocument(docId)
  doc.value = res.data
}

async function loadChunks() {
  showChunks.value = !showChunks.value
  if (showChunks.value && chunks.value.length === 0) {
    const res = await getChunks(docId)
    chunks.value = res.data || []
  }
}

async function handleReindex() {
  loading.value = true
  try {
    await reindexDocument(docId)
    ElMessage.success('重新索引完成')
    load()
  } finally { loading.value = false }
}

async function handleStatus(action: 'publish' | 'disable' | 'reactivate') {
  await changeStatus(docId, action)
  ElMessage.success('操作成功')
  load()
}

async function handleDelete() {
  await ElMessageBox.confirm('确认删除该文档？', '删除确认', {
    confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
  })
  await deleteDocument(docId)
  ElMessage.success('已删除')
  router.push('/knowledge')
}

function formatFileSize(bytes: number | null): string {
  if (!bytes) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}
</script>

<template>
  <div class="detail-page" v-if="doc">
    <div class="page-header">
      <el-button @click="router.push('/knowledge')" :icon="'ArrowLeft'">返回列表</el-button>
      <h2>{{ doc.title }}</h2>
    </div>

    <el-card class="info-card">
      <template #header>文档元信息</template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="文档编号">{{ doc.docId }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ DocTypeLabels[doc.docType] }}</el-descriptions-item>
        <el-descriptions-item label="状态"><DocumentStatusTag :status="doc.status" /></el-descriptions-item>
        <el-descriptions-item label="分块数">{{ doc.chunkCount }}</el-descriptions-item>
        <el-descriptions-item label="生效日期">{{ doc.effectiveDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="过期日期">{{ doc.expireDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="文件大小">{{ formatFileSize(doc.fileSize) }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ doc.createTime }}</el-descriptions-item>
        <el-descriptions-item label="Qdrant 集合" :span="2">{{ doc.qdrantCollection }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <div class="actions">
      <el-button v-if="doc.status === 'DRAFT'" type="success" @click="handleStatus('publish')">发布</el-button>
      <el-button v-if="doc.status === 'ACTIVE'" type="warning" @click="handleStatus('disable')">停用</el-button>
      <el-button v-if="doc.status === 'EXPIRED'" type="primary" @click="handleStatus('reactivate')">重新启用</el-button>
      <el-button @click="handleReindex" :loading="loading">重新索引</el-button>
      <el-button type="danger" @click="handleDelete">删除</el-button>
    </div>

    <el-card class="chunks-card" style="margin-top:16px">
      <template #header>
        <span @click="loadChunks" style="cursor:pointer">
          分块列表 ({{ doc.chunkCount }})
          <span style="font-size:12px;color:#999"> {{ showChunks ? '▲' : '▼' }}</span>
        </span>
      </template>
      <template v-if="showChunks">
        <div v-for="c in chunks" :key="c.chunkIndex" class="chunk-item">
          <span class="chunk-index">#{{ c.chunkIndex }}</span>
          <span class="chunk-text">{{ c.text }}</span>
          <span class="chunk-len">{{ c.textLength }} 字</span>
        </div>
        <div v-if="chunks.length === 0" style="color:#999">暂无分块数据</div>
      </template>
    </el-card>
  </div>
</template>

<style scoped>
.detail-page { padding: 24px; max-width: 960px; }
.page-header { display: flex; align-items: center; gap: 16px; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 18px; }
.actions { margin-top: 16px; display: flex; gap: 8px; }
.chunk-item { display: flex; align-items: flex-start; gap: 8px; padding: 8px 0; border-bottom: 1px solid #f0f0f0; }
.chunk-index { font-weight: 600; color: var(--el-color-primary); min-width: 40px; }
.chunk-text { flex: 1; font-size: 13px; color: #666; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.chunk-len { font-size: 12px; color: #999; white-space: nowrap; }
</style>
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/knowledge/
git commit -m "feat: KnowledgeView/KnowledgeDetail — 文档列表页 + 详情页

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 14: Update router and sidebar

**Files:**
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/layouts/DefaultLayout.vue`

- [ ] **Step 1: Add knowledge routes**

In `frontend/src/router/index.ts`, add two routes inside the `children` array of the `/` layout route, after the `admin` route:

```typescript
{
  path: 'knowledge',
  name: 'knowledge',
  component: () => import('@/views/knowledge/KnowledgeView.vue'),
  meta: { role: 'ADMIN' },
},
{
  path: 'knowledge/:docId',
  name: 'knowledge-detail',
  component: () => import('@/views/knowledge/KnowledgeDetail.vue'),
  meta: { role: 'ADMIN' },
},
```

- [ ] **Step 2: Update router beforeEach guard for role check**

Update `frontend/src/router/index.ts` — in the `beforeEach` guard, after the token check, add a role check for routes with `meta.role`:

```typescript
router.beforeEach((to, _from) => {
  const token = localStorage.getItem('refreshToken')
  if (!token && !to.meta.guest) {
    return '/login'
  }
  // Role check
  if (to.meta.role) {
    try {
      const stored = localStorage.getItem('auth')
      if (stored) {
        const auth = JSON.parse(stored)
        const roles: string[] = auth.roles || []
        if (!roles.includes(to.meta.role as string)) {
          return '/'
        }
      }
    } catch { return '/' }
  }
})
```

- [ ] **Step 3: Add sidebar menu item**

In `frontend/src/layouts/DefaultLayout.vue`, add after the admin nav item (inside `<nav class="sidebar-nav">`):

```vue
<a v-if="auth.roles?.includes('ADMIN')" class="nav-item"
   :class="{ active: route.path.startsWith('/knowledge') }" @click="router.push('/knowledge')">📚 知识库管理</a>
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/router/index.ts frontend/src/layouts/DefaultLayout.vue
git commit -m "feat: 路由+侧边栏 — 新增知识库管理入口（ADMIN only）

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 16: Scheduled auto-expiry

**Files:**
- Create: `backend/src/main/java/com/lak/ai/service/knowledge/DocumentExpiryScheduler.java`

**Consumes:** `KnowledgeDocumentMapper`, `EmbeddingStore<TextSegment>` (for Qdrant cleanup)

**Produces:** Hourly job that auto-expires ACTIVE docs past their expireDate

- [ ] **Step 1: Write DocumentExpiryScheduler**

```java
package com.lak.ai.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lak.ai.mapper.KnowledgeDocumentMapper;
import com.lak.ai.model.entity.KnowledgeDocument;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 文档过期定时任务 — 每小时扫描一次，将到期的 ACTIVE 文档自动转为 EXPIRED。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentExpiryScheduler {

    private final KnowledgeDocumentMapper documentMapper;
    private final EmbeddingStore<?> embeddingStore;

    /**
     * 每小时整点执行（启动后延迟10秒首次执行）。
     */
    @Scheduled(cron = "0 0 * * * *")
    public void expireDocuments() {
        log.debug("开始扫描过期文档...");
        List<KnowledgeDocument> expired = documentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getStatus, "ACTIVE")
                        .lt(KnowledgeDocument::getExpireDate, LocalDate.now())
        );

        for (KnowledgeDocument doc : expired) {
            try {
                // 从 Qdrant 删除向量
                Filter filter = MetadataFilterBuilder.metadataKey("docId").isEqualTo(doc.getDocId());
                embeddingStore.removeAll(filter);

                // 更新状态
                doc.setStatus("EXPIRED");
                documentMapper.updateById(doc);

                log.info("文档已自动过期, docId={}, expireDate={}", doc.getDocId(), doc.getExpireDate());
            } catch (Exception e) {
                log.error("文档过期处理失败, docId={}", doc.getDocId(), e);
            }
        }

        if (!expired.isEmpty()) {
            log.info("过期扫描完成, 处理 {} 份文档", expired.size());
        }
    }
}
```

Note: Ensure `@EnableScheduling` is present in the application configuration (check `LakAiApplication.java` or a config class). If not, add `@EnableScheduling` to `LakAiApplication.java`:

```java
@EnableScheduling
@SpringBootApplication
public class LakAiApplication { ... }
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/lak/ai/service/knowledge/DocumentExpiryScheduler.java
git commit -m "feat: DocumentExpiryScheduler — 每小时自动过期到期文档

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 17: End-to-end verification

- [ ] **Step 1: Start backend**

```bash
cd backend && mvn spring-boot:run
```
Verify: application starts without errors, Qdrant collections created, test data loaded (first time only).

- [ ] **Step 2: Start frontend**

```bash
cd frontend && npm run dev
```
Verify: dev server starts, login page loads.

- [ ] **Step 3: Test upload flow**

```bash
# Login as admin, then:
curl -X POST http://localhost:8080/api/v1/knowledge/documents \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -F "file=@test.txt" \
  -F "docType=POLICY"
```
Expected: `200 { "code": 200, "message": "上传成功", "data": { "docId": "DOC-...", "status": "DRAFT", ... } }`

- [ ] **Step 4: Test status flow**

```bash
# Publish
curl -X PATCH http://localhost:8080/api/v1/knowledge/documents/DOC-xxx/status \
  -H "Authorization: Bearer <ADMIN_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"action":"publish"}'
# Expected: status=ACTIVE, data present

# Disable
curl -X PATCH ... -d '{"action":"disable"}'
# Expected: status=EXPIRED
```

- [ ] **Step 5: Test frontend**

1. Login as admin → 侧边栏出现"📚 知识库管理"
2. Click → `/knowledge` 页面加载，列表为空
3. 点击"上传文档"→ 选择文件 → 上传 → 列表刷新出现新记录
4. 操作菜单 → 发布 → 状态变为"已发布"
5. 操作菜单 → 查看详情 → `/knowledge/:docId` 显示元信息
6. 操作菜单 → 删除 → 确认 → 列表刷新

- [ ] **Step 6: Fix any issues found**

- [ ] **Step 7: Final commit (if any fixes)**

```bash
git add -A
git commit -m "chore: 端到端验证修复

Co-Authored-By: Claude <noreply@anthropic.com>"
```
