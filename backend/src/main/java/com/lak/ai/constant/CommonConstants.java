package com.lak.ai.constant;

/**
 * 全局公共常量。
 * <p>
 * 遵循阿里巴巴Java开发手册 — 跨模块公共常量集中管理，禁止魔法值散落代码。
 */
public final class CommonConstants {

    private CommonConstants() {}

    /** 项目基础信息 */
    public static final String APP_NAME = "lak-ai-platform";
    public static final String BASE_PACKAGE = "com.lak.ai";
    public static final String API_PREFIX = "/api/v1";

    /** TraceId Header */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_KEY = "traceId";

    /** JWT */
    public static final String AUTH_HEADER = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    /** 审计日志留存（月） */
    public static final int AUDIT_RETENTION_MONTHS = 6;

    /** 统一错误码 */
    public static final int CODE_SUCCESS = 200;
    public static final int CODE_UNAUTHORIZED = 401;
    public static final int CODE_FORBIDDEN = 403;
    public static final int CODE_NOT_FOUND = 404;
    public static final int CODE_TOO_MANY_REQUESTS = 429;
    public static final int CODE_SERVER_ERROR = 500;
    public static final int CODE_SERVICE_UNAVAILABLE = 503;
}
