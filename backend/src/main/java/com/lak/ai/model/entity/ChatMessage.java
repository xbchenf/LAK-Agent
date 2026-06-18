package com.lak.ai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 消息记录 — 无 updateTime。
 */
@Getter
@Setter
@ToString
@TableName("chat_message")
public class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private String sessionId;
    private String role;
    private String content;
    private Integer tokens;
    /** JSON 格式 — 溯源文档列表 */
    @TableField("source_docs")
    private String sourceDocs;
    private BigDecimal confidence;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
