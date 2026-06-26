package com.lak.ai.model.dto;

import lombok.Data;

/**
 * 状态变更请求体。
 */
@Data
public class StatusActionDTO {
    /** 操作: publish / disable / reactivate */
    private String action;
}
