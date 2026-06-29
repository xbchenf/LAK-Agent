package com.lak.ai.controller;

import com.lak.ai.common.response.ApiResponse;
import com.lak.ai.model.bo.ContextMessage;
import com.lak.ai.model.bo.HandoffSummaryBO;
import com.lak.ai.model.entity.Ticket;
import com.lak.ai.service.chat.HandoffSummaryGenerator;
import com.lak.ai.service.chat.OperatorChatService;
import com.lak.ai.service.chat.session.SessionManager;
import com.lak.ai.service.ticket.OperatorTicketService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 坐席接口 — /api/v1/operator/*
 */
@RestController
@RequestMapping("/api/v1/operator")
@RequiredArgsConstructor
public class OperatorController {

    private final OperatorTicketService operatorTicketService;
    private final SessionManager sessionManager;
    private final HandoffSummaryGenerator summaryGenerator;
    private final OperatorChatService operatorChatService;

    /**
     * 待处理工单池
     */
    @GetMapping("/tickets/pending")
    public ApiResponse<List<Ticket>> pendingTickets(HttpServletRequest request) {
        java.util.List<String> roles = extractRoles(request);
        if (roles == null || (!roles.contains("ADMIN") && !roles.contains("OPERATOR"))) {
            return ApiResponse.error(403, "无权限");
        }
        return ApiResponse.success(operatorTicketService.listPending());
    }

    /**
     * 我所有工单（不限状态）
     */
    @GetMapping("/tickets/my-all")
    public ApiResponse<List<Ticket>> myAllTickets(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return ApiResponse.success(operatorTicketService.listMyAll(userId));
    }

    /**
     * 全量工单（管理员/坐席用）
     */
    @GetMapping("/tickets/all")
    public ApiResponse<List<Ticket>> allTickets(HttpServletRequest request) {
        @SuppressWarnings("unchecked")
        java.util.List<String> roles = (java.util.List<String>) request.getAttribute("roles");
        if (roles == null || (!roles.contains("ADMIN") && !roles.contains("OPERATOR"))) {
            return ApiResponse.error(403, "无权限");
        }
        return ApiResponse.success(operatorTicketService.listAll());
    }

    /**
     * 我处理中的工单
     */
    @GetMapping("/tickets/processing")
    public ApiResponse<List<Ticket>> myProcessingTickets(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return ApiResponse.success(operatorTicketService.listMyProcessing(userId));
    }

    /**
     * 工单详情（坐席视角）— 仅允许已认领人或未认领工单查看
     */
    @GetMapping("/tickets/{ticketNo}")
    public ApiResponse<Ticket> getTicket(@PathVariable String ticketNo, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Ticket ticket = operatorTicketService.getDetail(ticketNo);
        if (ticket == null) return ApiResponse.error(404, "工单不存在");
        // IDOR 防护：已认领工单仅认领人可查看详情
        if (ticket.getAssigneeId() != null && !userId.equals(ticket.getAssigneeId())) {
            return ApiResponse.error(403, "无权查看此工单");
        }
        return ApiResponse.success(ticket);
    }

    /**
     * 认领工单
     */
    @PostMapping("/tickets/{ticketNo}/claim")
    public ApiResponse<Ticket> claimTicket(@PathVariable String ticketNo, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            return ApiResponse.success(operatorTicketService.claim(ticketNo, userId));
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 提交处理
     */
    @PutMapping("/tickets/{ticketNo}")
    public ApiResponse<Ticket> processTicket(@PathVariable String ticketNo,
                                              @RequestBody ProcessTicketDTO dto,
                                              HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        // IDOR 防护：验证工单属于当前坐席
        Ticket ticket = operatorTicketService.getDetail(ticketNo);
        if (ticket == null) return ApiResponse.error(404, "工单不存在");
        if (ticket.getAssigneeId() == null || !userId.equals(ticket.getAssigneeId())) {
            return ApiResponse.error(403, "非本人认领的工单");
        }
        try {
            return ApiResponse.success(operatorTicketService.process(
                    ticketNo, userId, dto.getHandlerNotes(), dto.getTargetStatus()));
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    // ==================== 我的活跃会话 ====================

    @GetMapping("/sessions/my")
    public ApiResponse<List<WaitingSessionVO>> mySessions(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        java.util.Set<String> sessionIds = sessionManager.getOperatorSessions(userId);
        List<WaitingSessionVO> result = new ArrayList<>();
        for (String sessionId : sessionIds) {
            Map<Object, Object> fields = sessionManager.getAll(sessionId);
            if (fields.isEmpty()) continue;
            String lastMsg = getLastUserMessage(sessionId);
            String createTime = (String) fields.getOrDefault("createTime", "");
            result.add(new WaitingSessionVO(sessionId,
                    lastMsg.length() > 80 ? lastMsg.substring(0, 80) + "..." : lastMsg,
                    "", createTime, null));
        }
        return ApiResponse.success(result);
    }

    // ==================== 人工会话队列 ====================

    /**
     * 等待人工接入的会话列表
     */
    @GetMapping("/sessions/waiting")
    public ApiResponse<List<WaitingSessionVO>> waitingSessions() {
        List<String> queue = sessionManager.getWaitingQueue();
        List<WaitingSessionVO> result = new ArrayList<>();
        for (String sessionId : queue) {
            Map<Object, Object> fields = sessionManager.getAll(sessionId);
            if (fields.isEmpty()) continue;
            // 过滤掉已被接管或已关闭的会话
            String status = (String) fields.getOrDefault("status", "");
            if ("HUMAN_HANDLING".equals(status) || "CLOSED".equals(status)) continue;
            String lastMsg = getLastUserMessage(sessionId);
            String transferReason = (String) fields.getOrDefault("transferReason", "");
            String createTime = (String) fields.getOrDefault("createTime", "");
            // 读取缓存的摘要
            HandoffSummaryBO summary = summaryGenerator.getCached(sessionId);
            result.add(new WaitingSessionVO(
                    sessionId,
                    lastMsg.length() > 80 ? lastMsg.substring(0, 80) + "..." : lastMsg,
                    transferReason,
                    createTime,
                    summary
            ));
        }
        return ApiResponse.success(result);
    }

    private String getLastUserMessage(String sessionId) {
        try {
            var msgs = sessionManager.getMessages(sessionId);
            for (int i = msgs.size() - 1; i >= 0; i--) {
                if ("user".equals(msgs.get(i).getRole())) {
                    return msgs.get(i).getContent();
                }
            }
        } catch (Exception e) { /* ignore */ }
        return "";
    }

    // ==================== 坐席接管会话 ====================

    /**
     * 坐席接管会话
     */
    @PostMapping("/sessions/{sessionId}/takeover")
    public ApiResponse<Map<String, Object>> takeoverSession(@PathVariable String sessionId,
                                                             HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        try {
            var result = operatorChatService.takeover(sessionId, userId);
            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("sessionId", result.sessionId());
            data.put("history", result.history());
            return ApiResponse.success(data);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 获取会话消息（坐席端轮询）
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResponse<List<ContextMessage>> getMessages(@PathVariable String sessionId,
                                                          @RequestParam(defaultValue = "0") int since,
                                                          HttpServletRequest request) {
        // IDOR 防护：仅接管此会话的坐席可查看消息
        Long userId = (Long) request.getAttribute("userId");
        String storedOpId = (String) sessionManager.getAll(sessionId).getOrDefault("operatorId", "");
        if (storedOpId.isBlank() || !String.valueOf(userId).equals(storedOpId)) {
            return ApiResponse.error(403, "无权查看此会话");
        }
        if (since > 0) {
            return ApiResponse.success(operatorChatService.getNewOperatorMessages(sessionId, since, userId));
        }
        return ApiResponse.success(operatorChatService.getMessages(sessionId, userId));
    }

    /**
     * 坐席发送消息
     */
    @PostMapping("/sessions/{sessionId}/message")
    public ApiResponse<Void> sendMessage(@PathVariable String sessionId,
                                          @RequestBody SendMessageDTO dto,
                                          HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (!isSessionOwner(sessionId, userId)) return ApiResponse.error(403, "无权操作此会话");
        try {
            operatorChatService.sendMessage(sessionId, userId, dto.getContent());
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /**
     * 坐席关闭会话
     */
    @PostMapping("/sessions/{sessionId}/close")
    public ApiResponse<Void> closeSession(@PathVariable String sessionId,
                                           @RequestBody(required = false) CloseSessionDTO dto,
                                           HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (!isSessionOwner(sessionId, userId)) return ApiResponse.error(403, "无权操作此会话");
        try {
            operatorChatService.closeSession(sessionId, userId, dto != null ? dto.getNotes() : null);
            return ApiResponse.success(null);
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @Data
    public static class SendMessageDTO {
        @NotBlank private String content;
    }

    @Data
    public static class CloseSessionDTO {
        private String notes;
    }

    @SuppressWarnings("unchecked")
    private java.util.List<String> extractRoles(HttpServletRequest request) {
        return (java.util.List<String>) request.getAttribute("roles");
    }

    private boolean isSessionOwner(String sessionId, Long userId) {
        String storedOpId = (String) sessionManager.getAll(sessionId).getOrDefault("operatorId", "");
        return !storedOpId.isBlank() && String.valueOf(userId).equals(storedOpId);
    }

    @Data
    public static class WaitingSessionVO {
        private final String sessionId;
        private final String lastMessage;
        private final String transferReason;
        private final String createTime;
        private final HandoffSummaryBO summary;
    }

    @Data
    public static class ProcessTicketDTO {
        @NotBlank
        private String handlerNotes;
        private String targetStatus;  // COMPLETED / 为空默认 COMPLETED
    }
}
