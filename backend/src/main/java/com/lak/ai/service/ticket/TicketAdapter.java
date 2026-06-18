package com.lak.ai.service.ticket;

import com.lak.ai.mapper.TicketMapper;
import com.lak.ai.model.entity.Ticket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * 工单适配器 — 封装内部工单系统的接口调用。
 * <p>
 * 当前为最小实现：生成工单编号 + 写入 ticket 表。
 * 生产环境对接外部工单系统时，在此处替换为实际 HTTP/RPC 调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketAdapter {

    private final TicketMapper ticketMapper;

    /**
     * 创建工单 — 将 SlotFilling 结果写入 ticket 表。
     *
     * @param sessionId   关联会话ID
     * @param slotValues  SlotFilling 采集的字段值
     * @return 工单编号
     */
    public String createTicket(String sessionId, Map<String, String> slotValues) {
        String ticketNo = generateTicketNo();

        Ticket ticket = new Ticket();
        ticket.setTicketNo(ticketNo);
        ticket.setSessionId(sessionId);
        ticket.setComplaintType(slotValues.getOrDefault("complaintType", "OTHER"));
        ticket.setContactName(slotValues.getOrDefault("contactName", ""));
        ticket.setContactPhone(slotValues.getOrDefault("contactPhone", ""));
        ticket.setDescription(slotValues.getOrDefault("description", ""));
        ticket.setStatus("PENDING");
        ticket.setAttachmentUrl(slotValues.get("attachment"));

        ticketMapper.insert(ticket);
        log.info("工单创建成功, ticketNo={}, sessionId={}", ticketNo, sessionId);
        return ticketNo;
    }

    /**
     * 查询工单状态。
     */
    public Ticket queryTicket(String ticketNo) {
        return ticketMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getTicketNo, ticketNo));
    }

    private String generateTicketNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "TK-" + date + "-" + suffix;
    }
}
