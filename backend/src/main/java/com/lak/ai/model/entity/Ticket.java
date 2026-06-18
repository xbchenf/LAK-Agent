package com.lak.ai.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

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
}
