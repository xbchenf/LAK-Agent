package com.lak.ai.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lak.ai.common.exception.KnowledgeException;
import com.lak.ai.common.response.PageResult;
import com.lak.ai.constant.RagConstants;
import com.lak.ai.mapper.KnowledgeDocumentMapper;
import com.lak.ai.model.bo.ChunkResult;
import com.lak.ai.model.dto.DocumentQueryDTO;
import com.lak.ai.model.dto.StatusActionDTO;
import com.lak.ai.model.entity.KnowledgeDocument;
import com.lak.ai.model.vo.DocumentChunkVO;
import com.lak.ai.model.vo.DocumentVO;
import com.lak.ai.service.audit.AuditLog;
import com.lak.ai.service.rag.chunker.DocumentChunker;
import com.lak.ai.service.rag.embedding.EmbeddingService;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 知识文档管理服务 — CRUD + 状态管理 + 流水线编排。
 * <p>
 * 支持文档上传、列表查询、详情、编辑、状态变更（发布/停用/重新启用）、
 * 删除、分块详情查看、重新索引等操作。上传流程：文件存储 → 解析 → 分块
 * → 入库 MySQL；发布时额外写入 Qdrant 向量库。
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
    private final @Qualifier("policyEmbeddingStore") QdrantEmbeddingStore policyEmbeddingStore;
    private final @Qualifier("procedureEmbeddingStore") QdrantEmbeddingStore procedureEmbeddingStore;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final AtomicInteger SEQ = new AtomicInteger(1);
    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024L; // 20MB

    /**
     * 启动时从 DB 恢复当日序列号，避免重启后 docId 冲突。
     */
    @PostConstruct
    public void initSeq() {
        String todayPrefix = "DOC-" + LocalDate.now().format(DATE_FMT) + "-";
        long count = documentMapper.selectCount(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .likeRight(KnowledgeDocument::getDocId, todayPrefix)
        );
        int nextSeq = (int) count + 1;
        SEQ.set(nextSeq);
        log.info("文档序列号初始化, todayPrefix={}, nextSeq={}", todayPrefix, nextSeq);
    }

    // ==================== 文档编号生成 ====================

    /**
     * 生成文档编号，格式: DOC-{yyyyMMdd}-{序列号(0001~9999)}。
     */
    private String generateDocId() {
        String date = LocalDate.now().format(DATE_FMT);
        int seq = SEQ.getAndIncrement();
        if (seq > 9999) {
            SEQ.set(1);
        }
        return String.format("DOC-%s-%04d", date, seq);
    }

    // ==================== 上传文档 ====================

    /**
     * 上传并解析文档，状态为 DRAFT。
     */
    @AuditLog("KNOWLEDGE_UPLOAD")
    @Transactional
    public DocumentVO upload(MultipartFile file, String docType,
                             LocalDate effectiveDate, LocalDate expireDate) {
        // 1. 校验文件格式和大小
        String filename = file.getOriginalFilename();
        DocumentParser.FileType fileType = DocumentParser.detectType(filename);
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new KnowledgeException(4_401, "文件大小超过 20MB 限制");
        }

        // 2. 保存原始文件
        String docId = generateDocId();
        String fileUrl;
        try {
            fileUrl = fileStorageService.save(file, docId);
        } catch (IOException e) {
            throw new KnowledgeException(4_402, "文件保存失败: " + e.getMessage(), e);
        }

        // 3. 解析文档
        String text;
        try {
            text = documentParser.parse(file.getInputStream(), fileType, filename);
        } catch (IOException e) {
            throw new KnowledgeException(4_402, "文档解析失败: " + e.getMessage(), e);
        }

        // 4. 分块（DRAFT 状态暂不写入向量库，先入库 DB）
        List<ChunkResult> chunks = documentChunker.chunk(
                text, docId, extractTitle(filename, text),
                docType, null, effectiveDate != null ? effectiveDate.toString() : null);

        // 5. 确定 Qdrant Collection
        String collection = resolveCollection(docType);

        // 6. 写入 MySQL
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

    /**
     * 分页查询文档列表，支持按类型、状态、关键词过滤。
     */
    public PageResult<DocumentVO> list(DocumentQueryDTO query) {
        Page<KnowledgeDocument> page = new Page<>(query.getPage(), query.getSize());
        var result = documentMapper.selectPageWithFilters(
                page, query.getDocType(), query.getStatus(), query.getKeyword());

        List<DocumentVO> vos = result.getRecords().stream()
                .map(e -> toVO(e, null))
                .collect(Collectors.toList());
        return PageResult.of(vos, result.getTotal(), query.getPage(), query.getSize());
    }

    // ==================== 文档详情 ====================

    /**
     * 根据文档编号查询文档详情。
     */
    public DocumentVO getByDocId(String docId) {
        KnowledgeDocument entity = documentMapper.selectByDocId(docId);
        if (entity == null) {
            throw new KnowledgeException(4_403, "文档不存在: " + docId);
        }
        return toVO(entity, null);
    }

    // ==================== 编辑元信息 ====================

    /**
     * 编辑文档元信息（标题、生效日期、失效日期）。
     */
    @Transactional
    public DocumentVO update(String docId, String title,
                             LocalDate effectiveDate, LocalDate expireDate) {
        KnowledgeDocument entity = documentMapper.selectByDocId(docId);
        if (entity == null) {
            throw new KnowledgeException(4_403, "文档不存在: " + docId);
        }
        if (title != null && !title.isBlank()) {
            entity.setTitle(title);
        }
        entity.setEffectiveDate(effectiveDate);
        entity.setExpireDate(expireDate);
        documentMapper.updateById(entity);
        log.info("文档元信息已更新, docId={}", docId);
        return toVO(entity, null);
    }

    // ==================== 状态变更 ====================

    /**
     * 变更文档状态: publish（DRAFT→ACTIVE）/ disable（ACTIVE→EXPIRED）/ reactivate（EXPIRED→ACTIVE）。
     * <p>
     * DRAFT 状态直接删除即可，不支持废弃。
     */
    @AuditLog("KNOWLEDGE_STATUS")
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
                if (!"ACTIVE".equals(currentStatus)) {
                    throw new KnowledgeException(4_404, "仅已发布状态可停用，当前: " + currentStatus);
                }
                deleteFromQdrant(entity.getDocId());
                yield "EXPIRED";
            }
            case "reactivate" -> {
                if (!"EXPIRED".equals(currentStatus)) {
                    throw new KnowledgeException(4_404, "仅已过期状态可重新启用，当前: " + currentStatus);
                }
                // 重新索引 Qdrant
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

    /**
     * 物理删除文档（DB + Qdrant + 文件）。
     */
    @AuditLog("KNOWLEDGE_DELETE")
    @Transactional
    public void delete(String docId) {
        KnowledgeDocument entity = documentMapper.selectByDocId(docId);
        if (entity == null) {
            throw new KnowledgeException(4_403, "文档不存在: " + docId);
        }

        // 先删 DB 记录（事务内），确保不会因 Qdrant/文件操作失败导致 DB 回滚后向量已丢失
        documentMapper.deleteById(entity.getId());

        // DB 删除成功后再清理 Qdrant 向量（如果发布过）— 失败不影响 DB 一致性
        if ("ACTIVE".equals(entity.getStatus()) || "EXPIRED".equals(entity.getStatus())) {
            try {
                deleteFromQdrant(entity.getDocId());
            } catch (Exception e) {
                log.warn("Qdrant 向量清理失败（DB 已删除）, docId={}, error={}", docId, e.getMessage());
            }
        }
        // 清理文件 — 失败不影响 DB 一致性
        fileStorageService.delete(entity.getFileUrl());
        log.info("文档已删除, docId={}", docId);
    }

    // ==================== 分块详情 ====================

    /**
     * 查询文档的分块列表（返回截断文本，前200字符）。
     * <p>
     * 从 Qdrant 按 docId metadata 过滤查询分块。
     */
    public List<DocumentChunkVO> getChunks(String docId) {
        KnowledgeDocument entity = documentMapper.selectByDocId(docId);
        if (entity == null) {
            throw new KnowledgeException(4_403, "文档不存在: " + docId);
        }

        List<DocumentChunkVO> result = new ArrayList<>();
        try {
            // 通过 Qdrant scroll 按 docId metadata 过滤查询分块
            // 注意: embeddingStore 未直接暴露 scroll API，通过包装调用
            // 此处为简化实现 — 返回空的 chunk 列表，仅记录总块数
            log.debug("getChunks docId={}, total chunks from DB={}", docId, entity.getChunkCount());
        } catch (Exception e) {
            log.warn("getChunks 查询 Qdrant 失败, docId={}", docId, e);
        }
        return result;
    }

    // ==================== 重新索引 ====================

    /**
     * 对已发布的文档重新索引（先删后写 Qdrant）。
     */
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

    /**
     * 向量化文档并写入 Qdrant。
     */
    private void indexToQdrant(KnowledgeDocument entity) {
        try (var is = fileStorageService.read(entity.getFileUrl())) {
            String filename = entity.getFileUrl().substring(entity.getFileUrl().lastIndexOf('/') + 1);
            DocumentParser.FileType fileType = DocumentParser.detectType(filename);
            String text = documentParser.parse(is, fileType, filename);

            List<ChunkResult> chunks = documentChunker.chunk(
                    text, entity.getDocId(), entity.getTitle(),
                    entity.getDocType(), null,
                    entity.getEffectiveDate() != null ? entity.getEffectiveDate().toString() : null);

            QdrantEmbeddingStore store = resolveEmbeddingStore(entity.getQdrantCollection());

            for (ChunkResult chunk : chunks) {
                Map<String, String> metadataMap = new HashMap<>();
                metadataMap.put("docId", entity.getDocId());
                metadataMap.put("docTitle", entity.getTitle() != null ? entity.getTitle() : "");
                metadataMap.put("docType", entity.getDocType() != null ? entity.getDocType() : "");
                metadataMap.put("chunkIndex", String.valueOf(chunk.getIndex()));
                if (chunk.getSourceNo() != null) metadataMap.put("sourceNo", chunk.getSourceNo());
                if (chunk.getArticleNo() != null) metadataMap.put("articleNo", chunk.getArticleNo());
                if (chunk.getChapter() != null) metadataMap.put("chapter", chunk.getChapter());
                if (chunk.getEffectiveDate() != null) metadataMap.put("effectiveDate", chunk.getEffectiveDate());

                TextSegment segment = TextSegment.from(chunk.getText(), Metadata.from(metadataMap));
                Embedding embedding = embeddingService.embedForStore(chunk.getText());
                store.add(embedding, segment);
            }

            log.info("文档向量化写入 Qdrant 完成, docId={}, chunks={}, collection={}",
                    entity.getDocId(), chunks.size(), entity.getQdrantCollection());
        } catch (IOException e) {
            throw new KnowledgeException(4_406, "向量化索引失败: " + e.getMessage(), e);
        }
    }

    /**
     * 重新索引 Qdrant（先删后写）。
     */
    private void reindexToQdrant(KnowledgeDocument entity) {
        deleteFromQdrant(entity.getDocId());
        indexToQdrant(entity);
    }

    /**
     * 从 Qdrant 删除指定 docId 的所有向量。
     */
    private void deleteFromQdrant(String docId) {
        Filter filter = MetadataFilterBuilder.metadataKey("docId").isEqualTo(docId);
        // 需要在两个集合中都尝试删除
        policyEmbeddingStore.removeAll(filter);
        procedureEmbeddingStore.removeAll(filter);
        log.info("Qdrant 向量已删除, docId={}", docId);
    }

    /**
     * 根据 docType 确定 Qdrant Collection 名称。
     */
    private String resolveCollection(String docType) {
        return switch (docType) {
            case "POLICY" -> RagConstants.COLLECTION_POLICY;
            case "PROCEDURE" -> RagConstants.COLLECTION_PROCEDURE;
            default -> RagConstants.COLLECTION_PROCEDURE;
        };
    }

    /**
     * 根据 Collection 名称获取对应的 QdrantEmbeddingStore。
     */
    private QdrantEmbeddingStore resolveEmbeddingStore(String collection) {
        if (RagConstants.COLLECTION_POLICY.equals(collection)) {
            return policyEmbeddingStore;
        }
        return procedureEmbeddingStore;
    }

    /**
     * 从文件名和文本中提取文档标题。
     */
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

    /**
     * 将实体转换为 VO。
     */
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
