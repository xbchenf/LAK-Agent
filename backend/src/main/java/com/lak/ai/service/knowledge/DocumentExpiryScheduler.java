package com.lak.ai.service.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lak.ai.mapper.KnowledgeDocumentMapper;
import com.lak.ai.model.entity.KnowledgeDocument;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import dev.langchain4j.store.embedding.qdrant.QdrantEmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 文档过期定时任务 — 每小时扫描一次，将到期的 ACTIVE 文档自动转为 EXPIRED。
 * <p>
 * 扫描条件：status = ACTIVE AND expireDate &lt; 当前日期。
 * 处理动作：删除 Qdrant 向量 → 更新 DB 状态为 EXPIRED。
 * 支持两个 Collection：lak_policy_docs 和 lak_procedure_docs。
 */
@Slf4j
@Component
public class DocumentExpiryScheduler {

    private final KnowledgeDocumentMapper documentMapper;
    private final QdrantEmbeddingStore policyEmbeddingStore;
    private final QdrantEmbeddingStore procedureEmbeddingStore;

    public DocumentExpiryScheduler(
            KnowledgeDocumentMapper documentMapper,
            @Qualifier("policyEmbeddingStore") QdrantEmbeddingStore policyEmbeddingStore,
            @Qualifier("procedureEmbeddingStore") QdrantEmbeddingStore procedureEmbeddingStore) {
        this.documentMapper = documentMapper;
        this.policyEmbeddingStore = policyEmbeddingStore;
        this.procedureEmbeddingStore = procedureEmbeddingStore;
    }

    /**
     * 每小时整点执行（启动后延迟 10 秒首次执行）。
     */
    @Scheduled(cron = "0 0 * * * *")
    public void expireDocuments() {
        log.debug("开始扫描过期文档...");

        List<KnowledgeDocument> expired = documentMapper.selectList(
                new LambdaQueryWrapper<KnowledgeDocument>()
                        .eq(KnowledgeDocument::getStatus, "ACTIVE")
                        .lt(KnowledgeDocument::getExpireDate, LocalDate.now())
        );

        if (expired.isEmpty()) {
            return;
        }

        for (KnowledgeDocument doc : expired) {
            try {
                // 从 Qdrant 删除向量（两个 Collection 都尝试删除）
                Filter filter = MetadataFilterBuilder.metadataKey("docId").isEqualTo(doc.getDocId());
                policyEmbeddingStore.removeAll(filter);
                procedureEmbeddingStore.removeAll(filter);

                // 更新 DB 状态
                doc.setStatus("EXPIRED");
                documentMapper.updateById(doc);

                log.info("文档已自动过期, docId={}, title={}, expireDate={}",
                        doc.getDocId(), doc.getTitle(), doc.getExpireDate());
            } catch (Exception e) {
                log.error("文档过期处理失败, docId={}, title={}", doc.getDocId(), doc.getTitle(), e);
            }
        }

        log.info("过期扫描完成, 共处理 {} 份文档", expired.size());
    }
}
