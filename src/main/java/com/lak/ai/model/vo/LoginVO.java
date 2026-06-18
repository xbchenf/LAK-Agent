package com.lak.ai.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoginVO {

    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private Long userId;
    private String username;
    private String realName;
    private List<String> roles;
}
