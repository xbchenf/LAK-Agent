package com.lak.ai.controller;

import com.lak.ai.common.response.ApiResponse;
import com.lak.ai.model.dto.LoginDTO;
import com.lak.ai.model.dto.RefreshTokenDTO;
import com.lak.ai.model.vo.CaptchaVO;
import com.lak.ai.model.vo.LoginVO;
import com.lak.ai.service.security.AuthService;
import com.lak.ai.service.security.CaptchaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;

    /**
     * 登录 — POST /api/v1/auth/login
     */
    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        LoginVO vo = authService.login(dto);
        return ApiResponse.success(vo);
    }

    /**
     * 获取验证码 — GET /api/v1/auth/captcha
     * <p>
     * 开发环境返回 captchaKey + 明文 captchaText（前端直接展示）。
     * 生产环境必须替换为 captchaKey + base64 PNG 图片，禁止返回明文验证码。
     */
    @GetMapping("/captcha")
    public ApiResponse<CaptchaVO> captcha() {
        CaptchaService.CaptchaResult result = captchaService.generate();
        CaptchaVO vo = CaptchaVO.builder()
                .captchaKey(result.key())
                .captchaText(result.code())  // TODO: 生产环境替换为 base64 图片
                .build();
        return ApiResponse.success(vo);
    }

    /**
     * 刷新 Token — POST /api/v1/auth/refresh
     */
    @PostMapping("/refresh")
    public ApiResponse<LoginVO> refresh(@Valid @RequestBody RefreshTokenDTO dto) {
        LoginVO vo = authService.refresh(dto);
        return ApiResponse.success(vo);
    }
}
