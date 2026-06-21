package com.lak.ai.model.dto;

import lombok.Data;

@Data
public class UserCreateDTO {
    private String username;
    private String password;
    private String realName;
    private Long roleId;
}
