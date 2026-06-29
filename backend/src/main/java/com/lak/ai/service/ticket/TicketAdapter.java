package com.lak.ai.service.ticket;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lak.ai.mapper.ChatSessionMapper;
import com.lak.ai.mapper.SysUserMapper;
import com.lak.ai.mapper.TicketMapper;
import com.lak.ai.model.entity.ChatSession;
import com.lak.ai.model.entity.SysUser;
import com.lak.ai.model.entity.Ticket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 工单适配器 — 封装内部工单系统的接口调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketAdapter {

    private final TicketMapper ticketMapper;
    private final ChatSessionMapper sessionMapper;
    private final SysUserMapper sysUserMapper;

    /**
     * 创建工单 — 将 SlotFilling 结果写入 ticket 表。
     *
     * @param sessionId   关联会话ID
     * @param slotValues  SlotFilling 采集的字段值
     * @return 工单编号
     */
    public String createTicket(String sessionId, Map<String, String> slotValues) {
        String ticketNo = generateTicketNo();

        // 联系人姓名：优先从已登录用户取，兜底用槽值
        String contactName = "";
        ChatSession session = sessionMapper.selectOne(
                new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getSessionId, sessionId));
        if (session != null && session.getUserId() != null) {
            SysUser user = sysUserMapper.selectById(session.getUserId());
            contactName = (user != null && user.getRealName() != null) ? user.getRealName() : "";
        }
        if (contactName.isBlank()) {
            contactName = slotValues.getOrDefault("contactName", "");
        }

        Ticket ticket = new Ticket();
        ticket.setTicketNo(ticketNo);
        ticket.setSessionId(sessionId);
        ticket.setComplaintType(slotValues.getOrDefault("complaintType", "OTHER"));
        ticket.setContactName(contactName);
        ticket.setContactPhone(slotValues.getOrDefault("contactPhone", ""));
        ticket.setDescription(slotValues.getOrDefault("description", ""));
        ticket.setStatus("PENDING");

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

    public List<Ticket> queryUserTickets(Long userId) {
        var sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>()
                        .eq(ChatSession::getUserId, userId)
                        .select(ChatSession::getSessionId));
        List<String> sessionIds = sessions.stream().map(ChatSession::getSessionId).toList();
        if (sessionIds.isEmpty()) return List.of();
        return ticketMapper.selectList(
                new LambdaQueryWrapper<Ticket>()
                        .in(Ticket::getSessionId, sessionIds)
                        .orderByDesc(Ticket::getCreateTime));
    }

    /**
     * 查询所有工单（管理员/坐席用）
     */
    public List<Ticket> queryAllTickets() {
        return ticketMapper.selectList(
                new LambdaQueryWrapper<Ticket>()
                        .orderByDesc(Ticket::getCreateTime));
    }

    private String generateTicketNo() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        return "TK-" + date + "-" + suffix;
    }
}
