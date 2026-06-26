package com.lak.ai.service.rag;

import com.lak.ai.constant.RagConstants;
import io.qdrant.client.QdrantClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

/**
 * 测试数据加载器 — 启动时自动导入 test-data/ 下的文档到 Qdrant。
 * <p>
 * 如果 Qdrant Collection 中已有数据点，则跳过加载（防止重启时重复导入）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestDataLoader implements CommandLineRunner {

    private final DataIngestionService ingestionService;
    private final QdrantClient qdrantClient;

    @Value("${lak.test-data.load-on-startup:true}")
    private boolean loadOnStartup;

    @Override
    public void run(String... args) {
        if (!loadOnStartup) return;
        if (hasExistingData()) return;
        // 政策法规 → lak_policy_docs
        loadFiles("lak_policy_docs", "test-data/policy-001-治安管理处罚法实施条例.txt",
                                           "test-data/policy-002-旅馆业治安管理办法.txt");
        // 办事指南 → lak_procedure_docs
        loadFiles("lak_procedure_docs", "test-data/procedure-001-居民身份证办理指南.txt",
                                         "test-data/procedure-002-无犯罪记录证明办理指南.txt",
                                         "test-data/procedure-003-户口迁移办理指南.txt",
                                         "test-data/procedure-004-居住证办理指南.txt");
    }

    /**
     * 检查 Qdrant 是否已有测试数据，避免重启时重复加载。
     */
    private boolean hasExistingData() {
        try {
            long count = qdrantClient.countAsync(RagConstants.COLLECTION_POLICY).get();
            if (count > 0) {
                log.info("Qdrant 已有测试数据 ({} points)，跳过 TestDataLoader", count);
                return true;
            }
        } catch (ExecutionException | InterruptedException e) {
            log.warn("检查 Qdrant 数据状态异常，继续执行加载: {}", e.getMessage());
        }
        return false;
    }

    private void loadFiles(String collection, String... files) {
        for (String file : files) {
            try {
                var resource = new ClassPathResource(file);
                int chunks = ingestionService.ingest(resource.getInputStream(), collection);
                log.info("导入完成: {} → {} chunks (collection={})", file, chunks, collection);
            } catch (Exception e) {
                log.warn("导入跳过 ({}): {}", file, e.getMessage());
            }
        }
    }
}
