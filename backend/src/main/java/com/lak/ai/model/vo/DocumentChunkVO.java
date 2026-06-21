package com.lak.ai.model.vo;

import lombok.Data;
import java.io.Serializable;

/**
 * 文档分块详情响应（调试用）。
 */
@Data
public class DocumentChunkVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer chunkIndex;
    /** 截断文本，前200字符 */
    private String text;
    private Integer textLength;
}
