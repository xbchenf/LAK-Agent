package com.lak.ai.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;

@Getter
@Setter
@ToString(callSuper = true)
@TableName("chat_session")
public class ChatSession extends BaseEntity {

    @TableField("session_id")
    private String sessionId;
    @TableField("user_id")
    private Long userId;
    private String status;
    @TableField("intent_type")
    private String intentType;
    private BigDecimal confidence;
}
