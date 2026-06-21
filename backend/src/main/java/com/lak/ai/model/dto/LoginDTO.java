package com.lak.ai.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 32)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32)
    private String password;

    @NotBlank(message = "验证码key不能为空")
    private String captchaKey;

    @NotBlank(message = "验证码不能为空")
    private String captchaCode;
}
