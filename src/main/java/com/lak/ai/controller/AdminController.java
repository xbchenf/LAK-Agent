package com.lak.ai.controller;

import com.lak.ai.common.response.ApiResponse;
import com.lak.ai.security.filter.SensitiveWordPreCheckFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理接口 — 敏感词热加载等运维操作。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final SensitiveWordPreCheckFilter sensitiveWordFilter;

    /**
     * 敏感词热加载 — POST /api/v1/admin/sensitive-words/reload
     */
    @PostMapping("/sensitive-words/reload")
    public ApiResponse<Map<String, Object>> reloadSensitiveWords() {
        try {
            ClassPathResource resource = new ClassPathResource("config/sensitive-words.txt");
            sensitiveWordFilter.reload(resource.getFile().toPath());
            return ApiResponse.success(Map.of(
                    "count", sensitiveWordFilter.getWordCount(),
                    "message", "敏感词库热加载完成"
            ));
        } catch (Exception e) {
            log.error("敏感词库加载失败", e);
            return ApiResponse.error(500, "敏感词库加载失败: " + e.getMessage());
        }
    }
}
