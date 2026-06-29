package com.lak.ai.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Setter
@ToString(callSuper = true)
@TableName("ticket")
public class Ticket extends BaseEntity {

    @TableField("ticket_no")
    private String ticketNo;
    @TableField("session_id")
    private String sessionId;
    @TableField("complaint_type")
    private String complaintType;
    @TableField("contact_name")
    private String contactName;
    /** AES-256 加密存储 */
    @TableField("contact_phone")
    private String contactPhone;
    private String description;
    @TableField("attachment_url")
    private String attachmentUrl;
    private String status;
    @TableField("external_ticket_id")
    private String externalTicketId;

    // === 人工坐席字段 ===
    /** 坐席用户ID */
    @TableField("assignee_id")
    private Long assigneeId;
    /** 接单时间 */
    @TableField("assigned_at")
    private LocalDateTime assignedAt;
    /** 处理完成时间 */
    @TableField("handled_at")
    private LocalDateTime handledAt;
    /** 坐席处理意见 */
    @TableField("handler_notes")
    private String handlerNotes;
    /** 优先级: NORMAL / URGENT / LOW */
    private String priority;
}
