package com.lak.ai.controller;

import com.lak.ai.common.response.ApiResponse;
import com.lak.ai.service.chat.ChatService;
import com.lak.ai.service.chat.ChatService.ChatResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * 对话控制器 — 发送消息（SSE流式 + JSON） + 会话管理。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 发送消息 — 支持 SSE 流式（Accept: text/event-stream）和 JSON 普通模式。
     */
    @PostMapping("/message")
    public Object sendMessage(@Valid @RequestBody ChatMessageDTO dto, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) {
            throw new com.lak.ai.common.exception.AuthException(1_001, "未认证");
        }

        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("text/event-stream")) {
            return handleSse(dto, userId);
        }
        return handleJson(dto, userId);
    }

    private SseEmitter handleSse(ChatMessageDTO dto, Long userId) {
        SseEmitter emitter = new SseEmitter(60_000L); // 60s 超时
        ChatResult result = chatService.processMessage(userId, dto.getSessionId(), dto.getMessage());

        // 在后台线程发送（SSE 异步）
        new Thread(() -> {
            try {
                if (result.error()) {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of("message", result.errorMessage())));
                } else {
                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data(Map.of(
                                    "sessionId", result.sessionId(),
                                    "answer", result.response().getAnswer(),
                                    "sources", result.response().getSources() != null
                                            ? result.response().getSources() : java.util.Collections.emptyList(),
                                    "confidence", result.response().getConfidence(),
                                    "intentType", result.response().getIntentType()
                            )));
                }
                emitter.complete();
            } catch (IOException e) {
                log.error("SSE发送失败", e);
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    private ApiResponse<Map<String, Object>> handleJson(ChatMessageDTO dto, Long userId) {
        ChatResult result = chatService.processMessage(userId, dto.getSessionId(), dto.getMessage());
        if (result.error()) {
            return ApiResponse.error(404, result.errorMessage());
        }
        return ApiResponse.success(Map.of(
                "sessionId", result.sessionId(),
                "answer", result.response().getAnswer(),
                "sources", result.response().getSources() != null
                        ? result.response().getSources() : java.util.Collections.emptyList(),
                "confidence", result.response().getConfidence(),
                "intentType", result.response().getIntentType()
        ));
    }

    /**
     * 会话列表 — GET /api/v1/chat/sessions
     */
    @GetMapping("/sessions")
    public ApiResponse<Map<String, Object>> listSessions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        // TODO: 实现会话列表查询（Step 8 补齐 SessionService）
        return ApiResponse.success(Map.of("records", java.util.Collections.emptyList(),
                "total", 0, "page", page, "size", size));
    }

    /**
     * 会话详情 — GET /api/v1/chat/sessions/{sessionId}
     */
    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<Object> getSession(@PathVariable String sessionId) {
        var fields = chatService.getSessionManager().getAll(sessionId);
        if (fields == null || fields.isEmpty()) {
            return ApiResponse.error(404, "会话不存在");
        }
        return ApiResponse.success(fields);
    }

    /**
     * 删除会话（软删除） — DELETE /api/v1/chat/sessions/{sessionId}
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable String sessionId) {
        chatService.getSessionManager().close(sessionId);
        return ApiResponse.success();
    }

    @Data
    public static class ChatMessageDTO {
        private String sessionId;
        @NotBlank @Size(max = 2000)
        private String message;
    }
}
