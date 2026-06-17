package com.lak.ai.security.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 敏感词前置校验 — 用户输入拦截。
 * <p>
 * Filter Chain order=1（最先执行，安全优先于一切业务逻辑）。
 * 敏感词库存储在配置文件中，支持运行时通过管理接口热加载。
 */
@Slf4j
@Component
@Order(1)
public class SensitiveWordPreCheckFilter implements Filter {

    private volatile Set<String> sensitiveWords = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        // 仅拦截消息发送接口的 POST 请求
        if (!isChatMessageEndpoint(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }
        String body = extractBody(httpRequest);
        if (containsSensitiveWord(body)) {
            log.warn("敏感词前置拦截触发, uri={}", httpRequest.getRequestURI());
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setStatus(403);
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.getWriter().write(
                    "{\"code\":403,\"message\":\"消息包含敏感内容，请调整后重试\",\"data\":null}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean isChatMessageEndpoint(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().contains("/api/v1/chat/message");
    }

    private boolean containsSensitiveWord(String text) {
        if (text == null || text.isBlank() || sensitiveWords.isEmpty()) {
            return false;
        }
        return sensitiveWords.stream().anyMatch(word -> text.contains(word));
    }

    private String extractBody(HttpServletRequest request) throws IOException {
        // 使用 ContentCachingRequestWrapper 或直接读取
        // 注意：Servlet InputStream 只能读一次，后续 AuditLogInterceptor 需要协作处理
        // 这里先做基础实现，后续通过与 AuditLogInterceptor 共享 HttpServletRequestWrapper 解决
        return "";
    }

    /**
     * 热加载敏感词库（由管理接口调用）。
     */
    public void reload(Path wordListPath) throws IOException {
        Set<String> newSet = ConcurrentHashMap.newKeySet();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(wordListPath), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // 跳过空行和注释行
                if (!line.isEmpty() && !line.startsWith("#")) {
                    newSet.add(line);
                }
            }
        }
        this.sensitiveWords = newSet;
        log.info("敏感词库热加载完成, path={}, count={}", wordListPath, newSet.size());
    }

    public int getWordCount() {
        return sensitiveWords.size();
    }
}
