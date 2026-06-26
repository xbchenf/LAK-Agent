package com.lak.ai.constant;

/**
 * RAG 检索相关常量。
 */
public final class RagConstants {

    private RagConstants() {}

    /** Qdrant Collection 名称 */
    public static final String COLLECTION_POLICY = "lak_policy_docs";
    public static final String COLLECTION_PROCEDURE = "lak_procedure_docs";

    /** 向量维度 */
    public static final int EMBEDDING_DIMENSION = 1024;

    /** 混合检索参数 */
    public static final int DENSE_TOP_K = 10;
    public static final int SPARSE_TOP_K = 10;
    public static final int FUSION_TOP_K = 5;
    public static final double SIMILARITY_THRESHOLD = 0.75;

    /** RRF 融合常数 */
    public static final int RRF_K = 60;

    /** 检索超时 */
    public static final int RETRIEVAL_TIMEOUT_SECONDS = 3;
}
