package com.lak.ai.model.bo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Agent 请求 — 主Agent/子Agent 统一入参。
 */
@Data
@Builder
public class AgentRequest {

    private String sessionId;
    private Long userId;
    private String message;
    /** 上下文消息（最近 N 轮） */
    private List<ContextMessage> context;
}
