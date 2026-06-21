package com.lak.ai.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

/**
 * 角色-权限关联表 — 联合主键，无独立 id。
 */
@Getter
@Setter
@ToString
@TableName("sys_role_permission")
public class SysRolePermission implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long roleId;
    private Long permissionId;
}
