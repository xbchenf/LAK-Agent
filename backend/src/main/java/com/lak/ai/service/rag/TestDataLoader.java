package com.lak.ai.service.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * 测试数据加载器 — 启动时自动导入 test-data/ 下的文档到 Qdrant。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestDataLoader implements CommandLineRunner {

    private final DataIngestionService ingestionService;

    @Value("${lak.test-data.load-on-startup:true}")
    private boolean loadOnStartup;

    @Override
    public void run(String... args) {
        if (!loadOnStartup) return;
        // 政策法规 → lak_policy_docs
        loadFiles("lak_policy_docs", "test-data/policy-001-行政复议法实施办法.txt",
                                           "test-data/policy-002-行政处罚程序规定.txt");
        // 办事指南 → lak_procedure_docs
        loadFiles("lak_procedure_docs", "test-data/procedure-001-行政执法证申领指南.txt",
                                         "test-data/procedure-002-行政复议申请指南.txt");
    }

    private void loadFiles(String collection, String... files) {
        for (String file : files) {
            try {
                Path path = new ClassPathResource(file).getFile().toPath();
                int chunks = ingestionService.ingest(path, collection);
                log.info("导入完成: {} → {} chunks (collection={})", file, chunks, collection);
            } catch (Exception e) {
                log.warn("导入跳过 ({}): {}", file, e.getMessage());
            }
        }
    }
}
