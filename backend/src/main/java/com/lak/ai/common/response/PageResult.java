package com.lak.ai.common.response;

import lombok.Getter;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页响应封装。
 * <p>
 * 遵循阿里巴巴Java开发手册 — 分页查询统一使用此类型返回。
 */
@Getter
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<T> records;
    private final long total;
    private final int page;
    private final int size;

    private PageResult(List<T> records, long total, int page, int size) {
        this.records = records != null ? records : Collections.emptyList();
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public static <T> PageResult<T> of(List<T> records, long total, int page, int size) {
        return new PageResult<>(records, total, page, size);
    }

    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(Collections.emptyList(), 0, page, size);
    }
}
