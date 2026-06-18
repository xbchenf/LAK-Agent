package com.lak.ai.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统权限 — 无 updateTime 字段。
 */
@Getter
@Setter
@ToString
@TableName("sys_permission")
public class SysPermission implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String permCode;
    private String permName;
    private String resourcePath;
    private String method;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
