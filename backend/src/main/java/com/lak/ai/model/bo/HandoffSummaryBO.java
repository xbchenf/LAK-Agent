package com.lak.ai.model.bo;

import lombok.Data;

/**
 * 转人工摘要 — LLM 生成的结构化会话摘要，坐席接入前预览。
 */
@Data
public class HandoffSummaryBO {

    /** 用户画像（如"王先生，未认证用户"） */
    private String userProfile;
    /** 识别出的意图 */
    private String intent;
    /** AI 置信度 */
    private double confidence;
    /** 用户核心问题（一句话概括） */
    private String coreQuestion;
    /** AI 检索到的相关法规/文件 */
    private String retrievedDocs;
    /** AI 已给出的答复摘要 */
    private String aiResponseSummary;
    /** 转接原因 */
    private String transferReason;
    /** 建议坐席核实或跟进的事项 */
    private String suggestedVerification;
    /** 会话统计 */
    private String stats;
}
