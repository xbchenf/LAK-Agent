package com.lak.ai.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;

import java.io.Serializable;

/**
 * 统一 API 响应封装。
 * <p>
 * 遵循阿里巴巴Java开发手册 — 所有 Controller 返回值使用此类型包装。
 *
 * @param <T> data 泛型
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int code;
    private final String message;
    private final T data;
    private final String traceId;

    private ApiResponse(int code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }

    // ===== 成功响应 =====

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "success", data, getCurrentTraceId());
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(200, message, data, getCurrentTraceId());
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(200, "success", null, getCurrentTraceId());
    }

    // ===== 失败响应 =====

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null, getCurrentTraceId());
    }

    public static <T> ApiResponse<T> error(int code, String message, T data) {
        return new ApiResponse<>(code, message, data, getCurrentTraceId());
    }

    private static String getCurrentTraceId() {
        try {
            return org.slf4j.MDC.get("traceId");
        } catch (Exception e) {
            return null;
        }
    }
}
