package com.lak.ai.controller;

import com.lak.ai.common.response.ApiResponse;
import com.lak.ai.model.entity.Ticket;
import com.lak.ai.service.chat.session.SessionManager;
import com.lak.ai.service.ticket.TicketAdapter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketAdapter ticketAdapter;
    private final SessionManager sessionManager;

    /**
     * 当前用户的工单列表 — GET /api/v1/tickets/mine
     */
    @GetMapping("/mine")
    public ApiResponse<java.util.List<Ticket>> myTickets(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        if (userId == null) return ApiResponse.error(401, "未认证");
        return ApiResponse.success(ticketAdapter.queryUserTickets(userId));
    }

    /**
     * 创建工单 — POST /api/v1/tickets
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> createTicket(@Valid @RequestBody CreateTicketDTO dto) {
        Map<String, String> slotValues = Map.of(
                "complaintType", dto.getComplaintType(),
                "contactName", dto.getContactName(),
                "contactPhone", dto.getContactPhone(),
                "description", dto.getDescription()
        );
        String ticketNo = ticketAdapter.createTicket(dto.getSessionId(), slotValues);
        return ApiResponse.success(Map.of("ticketNo", ticketNo, "status", "PENDING"));
    }

    /**
     * 查询工单 — GET /api/v1/tickets/{ticketNo}
     */
    @GetMapping("/{ticketNo}")
    public ApiResponse<Ticket> getTicket(@PathVariable String ticketNo, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        Ticket ticket = ticketAdapter.queryTicket(ticketNo);
        if (ticket == null) {
            return ApiResponse.error(404, "工单不存在");
        }
        // IDOR 防护：验证工单关联的会话属于当前用户
        if (ticket.getSessionId() != null) {
            var sessionFields = sessionManager.getAll(ticket.getSessionId());
            Object sessionUserId = sessionFields.get("userId");
            if (sessionUserId != null && !sessionUserId.toString().equals(String.valueOf(userId))) {
                return ApiResponse.error(403, "无权访问此工单");
            }
        }
        // 脱敏：不返回完整手机号
        if (ticket.getContactPhone() != null && ticket.getContactPhone().length() > 4) {
            ticket.setContactPhone(ticket.getContactPhone().substring(0, 3) + "****"
                    + ticket.getContactPhone().substring(ticket.getContactPhone().length() - 4));
        }
        return ApiResponse.success(ticket);
    }

    @Data
    public static class CreateTicketDTO {
        private String sessionId;
        @NotBlank
        private String complaintType;
        @NotBlank @Size(max = 64)
        private String contactName;
        @NotBlank @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
        private String contactPhone;
        @NotBlank @Size(min = 10, max = 2000)
        private String description;
    }
}
