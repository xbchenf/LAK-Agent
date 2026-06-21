package com.lak.ai.constant;

/**
 * 对话相关常量。
 */
public final class ChatConstants {

    private ChatConstants() {}

    /** 会话 Redis Key 前缀 */
    public static final String SESSION_KEY_PREFIX = "session:";
    /** 会话 TTL 秒数 */
    public static final int SESSION_TTL_SECONDS = 1800;

    /** 上下文窗口 — 最大轮数 */
    public static final int MAX_CONTEXT_ROUNDS = 10;
    /** 上下文窗口 — Token 上限 */
    public static final int MAX_CONTEXT_TOKENS = 6000;

    /** 单条消息最大字符数 */
    public static final int MAX_MESSAGE_LENGTH = 2000;

    /** Slot-Filling 最大轮数 */
    public static final int MAX_SLOT_FILL_ROUNDS = 5;
}
