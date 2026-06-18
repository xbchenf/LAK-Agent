---
name: java-pagination
description: LAK-Agent 分页查询规范 — PageResult<T> 统一封装 + Mybatis-Plus 分页插件
---

# 分页查询规范（LAK-Agent）

## 通用 DTO

```java
// 分页查询请求 — 放在 model/dto/ 下
@Data
@EqualsAndHashCode(callSuper = true)
public class XxxQuery extends PageQuery {
    private String keyword;   // 搜索关键词
    // 其他查询条件...
}

// 分页查询公共基类（已有）
// com.lak.ai.model.dto.PageQuery { page, size }
```

## Controller 层

```java
@GetMapping
public ApiResponse<PageResult<XxxVO>> page(@Valid XxxQuery query) {
    PageResult<XxxVO> result = xxxService.page(query);
    return ApiResponse.success(result);
}
```

## Service 层

```java
public PageResult<XxxVO> page(XxxQuery query) {
    Page<Xxx> page = new Page<>(query.getPage(), query.getSize());
    LambdaQueryWrapper<Xxx> wrapper = new LambdaQueryWrapper<>();
    // 构建查询条件...
    Page<Xxx> result = xxxMapper.selectPage(page, wrapper);
    List<XxxVO> voList = result.getRecords().stream()
            .map(this::toVO)
            .toList();
    return PageResult.of(voList, result.getTotal(), query.getPage(), query.getSize());
}
```

## 强制约束

- 所有分页查询必须使用 `PageResult<T>` 封装，禁止返回原始 Mybatis-Plus `IPage`
- 分页请求参数统一继承 `PageQuery`（page 默认 1, size 默认 20, 最大 100）
- 禁止不带 LIMIT 的全表查询 — 必须显式设置分页或 LIMIT
- 大数据量导出场景不走分页接口，使用异步任务 + 文件下载
