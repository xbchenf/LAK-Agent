package com.lak.ai.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@Getter
@Setter
@ToString
@TableName("sys_menu_permission")
public class SysMenuPermission implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long menuId;
    private Long permissionId;
}
