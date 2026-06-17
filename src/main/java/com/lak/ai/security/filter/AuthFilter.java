package com.lak.ai.security.filter;

import com.lak.ai.common.exception.AuthException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

/**
 * JWT 认证过滤器。
 * <p>
 * Filter Chain order=4（在 TraceId、AuditLog 之后）。
 * 白名单端点无需认证（/health, /auth/login, /auth/captcha）。
 */
@Slf4j
@Component
@Order(4)
public class AuthFilter implements Filter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private static final Set<String> WHITELIST_PATHS = Set.of(
            "/api/v1/health",
            "/api/v1/auth/login",
            "/api/v1/auth/captcha",
            "/api/v1/auth/refresh"
    );

    @Value("${lak.jwt.secret:lak-ai-platform-default-secret-change-in-production}")
    private String jwtSecret;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        // 白名单放行（无需认证）
        if (WHITELIST_PATHS.stream().anyMatch(path::equals)) {
            chain.doFilter(request, response);
            return;
        }

        // 非白名单端点：必须提供有效 JWT
        String authHeader = httpRequest.getHeader(AUTH_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            sendUnauthorized(response, "LAK-01-001", "未认证，请先登录");
            return;
        }

        try {
            String token = authHeader.substring(BEARER_PREFIX.length());
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            // 将用户信息注入 request attribute，供 Controller 使用
            httpRequest.setAttribute("userId", claims.get("userId", Long.class));
            httpRequest.setAttribute("username", claims.getSubject());
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            httpRequest.setAttribute("roles", roles);
            chain.doFilter(request, response);
        } catch (ExpiredJwtException e) {
            log.info("JWT 已过期, path={}", path);
            sendUnauthorized(response, "LAK-01-006", "Access Token 已过期，请刷新");
        } catch (Exception e) {
            log.info("JWT 解析失败, path={}, reason={}", path, e.getMessage());
            sendUnauthorized(response, "LAK-01-001", "未认证，请先登录");
        }
    }

    private void sendUnauthorized(ServletResponse response, String errorCode, String message)
            throws IOException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setStatus(401);
        httpResponse.setContentType("application/json;charset=UTF-8");
        httpResponse.getWriter().write(String.format(
                "{\"code\":401,\"errorCode\":\"%s\",\"message\":\"%s\",\"data\":null}",
                errorCode, message));
    }
}
