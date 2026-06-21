package com.lak.ai.security.config;

import com.lak.ai.security.interceptor.AuditLogInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * WebMvc 配置 — 注册拦截器。
 * <p>
 * 注意：Filter 由 @Order 注解控制顺序，Interceptor 在此注册。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuditLogInterceptor auditLogInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 审计日志拦截器 — 拦截所有 /api/** 请求
        registry.addInterceptor(auditLogInterceptor)
                .addPathPatterns("/api/**")
                .order(3);
    }
}
