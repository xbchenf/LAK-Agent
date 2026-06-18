---
name: java-security-filter
description: LAK-Agent Filter Chain 安全过滤器开发规范。新增安全拦截器时自动应用。
---

# Filter Chain 开发规范（LAK-Agent）

## Filter Chain 顺序（不可变）

```
order=1  SensitiveWordPreCheckFilter    ← 敏感词前置校验（最先执行，安全优先）
order=2  TraceIdFilter                   ← 生成/提取 TraceId 注入 MDC
order=3  AuditLogInterceptor             ← 捕获请求体，记录入站日志
order=4  AuthFilter                      ← JWT 校验 → 未认证返回 401
order=5  RateLimitFilter                 ← Redis 滑动窗口限流 → 超限返回 429
order=6  RequestMapping                  ← Spring MVC 路由分发
```

## 新增 Filter 规则

若要新增 Filter：

1. **确定插入位置**: 在 Filter Chain 中选一个合理的 order 位置
2. **使用 @Order 注解**: `@Order(N)` 控制顺序
3. **使用 @Component**: 由 Spring 自动注册
4. **失败必须阻断**: 安全 Filter 发现问题时必须 `return` 不放行，禁止 `chain.doFilter()` 后静默通过

## Filter 实现模板

```java
@Slf4j
@Component
@Order(N)  // 从上述 Chain 中选择合适的 N
public class XxxFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // 白名单放行（如需要）
        if (isWhitelisted(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        // 安全校验
        if (shouldBlock(httpRequest)) {
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.getWriter().write(
                "{\"code\":403,\"message\":\"被拦截\",\"data\":null}");
            return;  // ← 必须 return，不放行
        }

        chain.doFilter(request, response);
    }
}
```

## 白名单管理

以下端点不经过 AuthFilter（在白名单中）：
- `GET /api/v1/health`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/captcha`
- `POST /api/v1/auth/refresh`

新增白名单端点时，必须在 AuthFilter 的 `WHITELIST_PATHS` 中同步更新。

## 强制约束

- Filter 不能有业务逻辑，只做拦截与校验
- 安全 Filter 失败必须显式返回错误状态码 + JSON body，不能只抛异常
- Body 读取使用 `CachedBodyHttpServletRequestWrapper`，保证下游可再次读取
- 敏感词词库路径: `src/main/resources/config/sensitive-words.txt`
