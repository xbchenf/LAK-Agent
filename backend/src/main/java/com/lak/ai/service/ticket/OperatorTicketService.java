package com.lak.ai.service.ticket;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lak.ai.mapper.TicketMapper;
import com.lak.ai.model.entity.Ticket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 坐席工单服务 — 工单认领、处理、关单
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperatorTicketService {

    private final TicketMapper ticketMapper;

    /**
     * 待处理工单池（未认领的 PENDING 工单）
     */
    public List<Ticket> listPending() {
        return ticketMapper.selectList(
                new LambdaQueryWrapper<Ticket>()
                        .isNull(Ticket::getAssigneeId)
                        .eq(Ticket::getStatus, "PENDING")
                        .orderByDesc(Ticket::getCreateTime));
    }

    /**
     * 全量工单
     */
    public List<Ticket> listAll() {
        return ticketMapper.selectList(
                new LambdaQueryWrapper<Ticket>()
                        .orderByDesc(Ticket::getCreateTime));
    }

    /**
     * 我所有工单（不限状态）
     */
    public List<Ticket> listMyAll(Long operatorId) {
        return ticketMapper.selectList(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getAssigneeId, operatorId)
                        .orderByDesc(Ticket::getCreateTime));
    }

    /**
     * 我处理中的工单
     */
    public List<Ticket> listMyProcessing(Long operatorId) {
        return ticketMapper.selectList(
                new LambdaQueryWrapper<Ticket>()
                        .eq(Ticket::getAssigneeId, operatorId)
                        .eq(Ticket::getStatus, "PROCESSING")
                        .orderByDesc(Ticket::getCreateTime));
    }

    /**
     * 认领工单
     */
    public Ticket claim(String ticketNo, Long operatorId) {
        Ticket ticket = ticketMapper.selectOne(
                new LambdaQueryWrapper<Ticket>().eq(Ticket::getTicketNo, ticketNo));
        if (ticket == null) throw new RuntimeException("工单不存在");
        if (!"PENDING".equals(ticket.getStatus())) throw new RuntimeException("工单已被认领或已处理");
        ticket.setAssigneeId(operatorId);
        ticket.setAssignedAt(LocalDateTime.now());
        ticket.setStatus("PROCESSING");
        ticketMapper.updateById(ticket);
        log.info("工单认领成功, ticketNo={}, operatorId={}", ticketNo, operatorId);
        return ticket;
    }

    /**
     * 提交处理结果
     */
    public Ticket process(String ticketNo, Long operatorId, String handlerNotes, String targetStatus) {
        Ticket ticket = ticketMapper.selectOne(
                new LambdaQueryWrapper<Ticket>().eq(Ticket::getTicketNo, ticketNo));
        if (ticket == null) throw new RuntimeException("工单不存在");
        if (!ticket.getAssigneeId().equals(operatorId)) throw new RuntimeException("非本人认领的工单");
        ticket.setHandlerNotes(handlerNotes);
        ticket.setHandledAt(LocalDateTime.now());
        ticket.setStatus(targetStatus != null ? targetStatus : "COMPLETED");
        ticketMapper.updateById(ticket);
        log.info("工单处理完成, ticketNo={}, status={}", ticketNo, ticket.getStatus());
        return ticket;
    }

    /**
     * 查询单个工单详情（坐席视角）
     */
    public Ticket getDetail(String ticketNo) {
        return ticketMapper.selectOne(
                new LambdaQueryWrapper<Ticket>().eq(Ticket::getTicketNo, ticketNo));
    }
}
