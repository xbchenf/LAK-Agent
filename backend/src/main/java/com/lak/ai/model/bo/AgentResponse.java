package com.lak.ai.model.bo;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Agent 响应 — 子Agent 统一出参。
 */
@Data
@Builder
public class AgentResponse {

    private String answer;
    private List<Map<String, Object>> sources;
    private double confidence;
    private String intentType;
    private Map<String, Object> extra;
}
