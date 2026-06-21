package com.lak.ai.model.vo;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 文档列表/详情响应。
 */
@Data
public class DocumentVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String docId;
    private String title;
    private String docType;
    private String status;
    private String fileUrl;
    private Long fileSize;
    private LocalDate effectiveDate;
    private LocalDate expireDate;
    private Integer chunkCount;
    private String qdrantCollection;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
