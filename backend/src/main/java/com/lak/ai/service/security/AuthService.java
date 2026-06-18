package com.lak.ai.service.security;

import com.lak.ai.model.dto.LoginDTO;
import com.lak.ai.model.dto.RefreshTokenDTO;
import com.lak.ai.model.vo.LoginVO;

/**
 * 认证服务接口。
 */
public interface AuthService {

    /**
     * 用户名+密码+验证码登录。
     */
    LoginVO login(LoginDTO dto);

    /**
     * 刷新 Access Token。
     */
    LoginVO refresh(RefreshTokenDTO dto);
}
