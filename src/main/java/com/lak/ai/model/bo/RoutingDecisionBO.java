package com.lak.ai.model.bo;

import com.lak.ai.enums.IntentType;
import lombok.Builder;
import lombok.Data;

/**
 * 路由决策 — MasterAgent 输出。
 */
@Data
@Builder
public class RoutingDecisionBO {

    /** 识别出的意图 */
    private IntentType intentType;
    /** 最终置信度 (0.0-1.0) */
    private double confidence;
    /** 路由目标 Agent ID（null = 兜底） */
    private String targetAgentId;
    /** 是否走兜底 */
    private boolean fallback;
    /** 分类依据（调试用） */
    private String reasoning;
    /** 分类耗时 */
    private long costMs;
}
