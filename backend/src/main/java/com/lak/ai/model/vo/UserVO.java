package com.lak.ai.model.vo;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class UserVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String realName;
    private String email;
    private String phone;
    private String status;
    private Long roleId;
    private String roleName;
    private LocalDateTime createTime;
}
