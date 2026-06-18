---
name: java-restful-api
description: RESTful Controller 标准接口模板。新增 Controller 接口时自动应用。使用 ApiResponse<T> 统一响应 + traceId 全链路追踪。
---

# RESTful API 接口规范（LAK-Agent）

## 触发条件

当需要新增 Controller 类或新增 API 端点时，自动应用此 Skill。

## 标准模板

```java
@Slf4j
@RestController
@RequestMapping("/api/v1/xxx")
@RequiredArgsConstructor
public class XxxController {

    private final XxxService xxxService;

    @PostMapping
    public ApiResponse<Void> add(@Valid @RequestBody XxxAddDTO dto) {
        xxxService.add(dto);
        return ApiResponse.success();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        xxxService.delete(id);
        return ApiResponse.success();
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @Valid @RequestBody XxxUpdateDTO dto) {
        xxxService.update(id, dto);
        return ApiResponse.success();
    }

    @GetMapping("/{id}")
    public ApiResponse<XxxVO> get(@PathVariable Long id) {
        return ApiResponse.success(xxxService.getById(id));
    }

    @GetMapping
    public ApiResponse<PageResult<XxxVO>> page(@Valid XxxQuery query) {
        return ApiResponse.success(xxxService.page(query));
    }
}
```

## 强制约束

- 所有接口统一返回 `ApiResponse<T>`（`com.lak.ai.common.response.ApiResponse`）
- 分页接口统一返回 `PageResult<T>`（`com.lak.ai.common.response.PageResult`）
- 请求参数必须用 `@Valid` 开启 Bean Validation
- POST/PUT 必须定义专用 DTO，禁止直接收 Entity（DO）
- Controller 禁止包含业务逻辑，只做参数校验 + DTO/VO 转换 + 调用 Service
- traceId 由 ApiResponse 自动注入（从 MDC 读取），无需手动设置
- API 路径前缀统一为 `/api/v1/`
