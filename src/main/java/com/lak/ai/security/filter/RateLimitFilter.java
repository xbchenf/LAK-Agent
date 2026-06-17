package com.lak.ai.security.filter;

import com.lak.ai.common.exception.RateLimitException;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * 限流过滤器 — 基于 Redis Lua 脚本滑动窗口。
 * <p>
 * Filter Chain order=5（最后执行，Auth 校验完成后再限流）。
 * Key 格式: rate_limit:{userId}:{apiPath}
 */
@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
public class RateLimitFilter implements Filter {

    private final StringRedisTemplate redisTemplate;

    private static final String LUA_SCRIPT = """
            local key = KEYS[1]
            local window = tonumber(ARGV[1])
            local limit = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            redis.call('ZREMRANGEBYSCORE', key, 0, now - window * 1000)
            local count = redis.call('ZCARD', key)
            if count >= limit then
                return 0
            end
            redis.call('ZADD', key, now, now .. '-' .. count)
            redis.call('EXPIRE', key, math.ceil(window))
            return limit - count
            """;

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);

    @Value("${lak.rate-limit.max-requests-per-minute:30}")
    private int maxRequestsPerMinute;

    @Value("${lak.rate-limit.window-seconds:60}")
    private int windowSeconds;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String userId = (String) httpRequest.getAttribute("userId");
        if (userId == null) {
            userId = httpRequest.getRemoteAddr();  // 未认证用户按 IP 限流
        }
        String key = "rate_limit:" + userId + ":" + httpRequest.getRequestURI();
        long now = System.currentTimeMillis();
        Long remaining = redisTemplate.execute(
                SCRIPT,
                List.of(key),
                String.valueOf(windowSeconds),
                String.valueOf(maxRequestsPerMinute),
                String.valueOf(now)
        );
        if (remaining != null && remaining <= 0) {
            log.warn("限流触发, userId={}, path={}", userId, httpRequest.getRequestURI());
            throw new RateLimitException(2_102, "请求过于频繁，请稍后再试");
        }
        chain.doFilter(request, response);
    }
}
