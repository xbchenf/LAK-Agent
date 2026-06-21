---
name: java-exception
description: LAK-Agent 异常处理规范 — BusinessException 6子类体系 + GlobalExceptionHandler 统一拦截
---

# 异常处理规范（LAK-Agent）

## 异常体系

```
RuntimeException
  └── BusinessException               # 基类 (code + message)
        ├── AuthException             # 认证异常 → HTTP 401
        ├── ChatException             # 对话异常 → HTTP 400/404/504
        ├── TicketException           # 工单异常 → HTTP 500
        ├── SensitiveWordException    # 敏感词拦截 → HTTP 403（不返回详情）
        ├── RateLimitException        # 限流 → HTTP 429
        └── ModelException            # 大模型异常 → HTTP 503
```

## 使用规范

```java
// ✅ 正确: 抛语义化异常
if (session == null) {
    throw new ChatException(2_104, "会话不存在或已关闭");
}

// ❌ 错误: 使用通用 RuntimeException
throw new RuntimeException("会话不存在");

// ✅ 正确: 异常信息包含关键参数
throw new ModelException(99_501, String.format("大模型调用超时, model=%s, timeout=%ds", modelName, timeout));

// ❌ 错误: 空 catch 块
try { ... } catch (Exception e) {}

// ✅ 正确: 记录日志后重新抛出
try { ... } catch (Exception e) {
    log.error("工单创建失败, sessionId={}", sessionId, e);
    throw new TicketException(3_202, "工单提交失败，请稍后重试");
}
```

## 错误码格式

`LAK-XX-XXX`（5位数字）：
- 模块: 01=Auth, 02=Chat, 03=Ticket, 98=Validation, 99=System
- 编号: 3位数字，模块内唯一

```java
throw new AuthException(1_001, "未认证，请先登录");       // LAK-01-001
throw new ChatException(2_101, "消息包含敏感内容");       // LAK-02-101
throw new TicketException(3_202, "工单不存在");           // LAK-03-202
```

## 禁止事项

- 禁止在 Controller 内 try-catch（交给 GlobalExceptionHandler 统一处理）
- 禁止 `catch (Exception e)` 吞掉异常
- 禁止在循环内 try-catch
- 禁止 `e.printStackTrace()`（使用 `log.error()`）
