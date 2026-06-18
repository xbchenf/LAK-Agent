package com.lak.ai.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

@Getter
@Setter
@ToString(callSuper = true)
@TableName("knowledge_document")
public class KnowledgeDocument extends BaseEntity {

    @TableField("doc_id")
    private String docId;
    private String title;
    @TableField("doc_type")
    private String docType;
    @TableField("file_url")
    private String fileUrl;
    @TableField("effective_date")
    private LocalDate effectiveDate;
    @TableField("expire_date")
    private LocalDate expireDate;
    private String status;
    @TableField("chunk_count")
    private Integer chunkCount;
    @TableField("qdrant_collection")
    private String qdrantCollection;
}
