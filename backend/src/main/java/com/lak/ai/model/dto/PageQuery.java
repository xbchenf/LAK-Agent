package com.lak.ai.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页查询公共基类。
 */
@Data
public class PageQuery {

    @Min(1)
    private int page = 1;

    @Min(1)
    @Max(100)
    private int size = 20;
}
