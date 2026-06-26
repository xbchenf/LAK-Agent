package com.lak.ai.model.vo;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

@Data
public class RoleVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String roleCode;
    private String roleName;
    private String description;
    private List<Long> menuIds;
}
