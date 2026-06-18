package com.lak.ai.model.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    private String roleCode;
    private String roleName;
    private String description;
}
