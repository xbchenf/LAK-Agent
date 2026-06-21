package com.lak.ai.model.bo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 上下文窗口中的单条消息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextMessage {

    private String role;       // user / assistant / system
    private String content;
    private long timestamp;
}
