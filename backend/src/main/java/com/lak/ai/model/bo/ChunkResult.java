package com.lak.ai.model.bo;

import lombok.Builder;
import lombok.Data;

/**
 * 文档分块结果 — DocumentChunker 产出。
 */
@Data
@Builder
public class ChunkResult {

    /** 块文本 */
    private String text;
    /** 块序号 */
    private int index;
    /** 文档总块数 */
    private int totalChunks;

    // ===== 元数据 =====
    private String docId;
    private String docTitle;
    private String docType;
    private String sourceNo;
    private String publishDept;
    private String effectiveDate;
    private String expireDate;
    private String chapter;
    private String articleNo;
    private String prevChunkId;
    private String nextChunkId;
    private String overlapPrefix;
    private String keywords;
    private int version;
}
