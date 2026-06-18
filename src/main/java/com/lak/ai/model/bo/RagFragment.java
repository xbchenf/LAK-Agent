package com.lak.ai.model.bo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

/**
 * RAG 检索片段 — 混合检索返回的单条结果。
 */
@Data
@Builder
public class RagFragment {

    /** Qdrant Point ID */
    private String pointId;
    /** 片段文本 */
    private String text;
    /** 综合得分（RRF 融合后） */
    private double score;

    // ===== 溯源元数据 =====
    private String docId;
    private String docTitle;
    private String sourceNo;
    private String articleNo;
    private String chapter;
    private LocalDate effectiveDate;
    private LocalDate expireDate;
    private Integer chunkIndex;
    private String prevChunkId;
    private String nextChunkId;

    /** Qdrant Payload 原始数据 */
    private Map<String, Object> rawPayload;
}
