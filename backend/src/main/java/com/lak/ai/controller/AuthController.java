package com.lak.ai.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lak.ai.common.response.ApiResponse;
import com.lak.ai.mapper.SysUserMapper;
import com.lak.ai.model.dto.LoginDTO;
import com.lak.ai.model.dto.RefreshTokenDTO;
import com.lak.ai.model.entity.SysUser;
import com.lak.ai.model.vo.CaptchaVO;
import com.lak.ai.model.vo.LoginVO;
import com.lak.ai.service.security.AuthService;
import com.lak.ai.service.security.CaptchaService;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

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
     * 注册 — POST /api/v1/auth/register
     */
    @PostMapping("/register")
    public ApiResponse<Void> register(@RequestBody RegisterDTO dto) {
        // 验证码校验
        if (!captchaService.validate(dto.getCaptchaKey(), dto.getCaptchaCode())) {
            return ApiResponse.error(400, "验证码错误或已过期");
        }
        if (dto.getUsername() == null || dto.getUsername().isBlank() || dto.getUsername().length() < 2) {
            return ApiResponse.error(400, "用户名至少2位");
        }
        if (dto.getPassword() == null || dto.getPassword().length() < 6) {
            return ApiResponse.error(400, "密码至少6位");
        }
        if (sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, dto.getUsername())) > 0) {
            return ApiResponse.error(400, "用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setRealName(dto.getRealName() != null ? dto.getRealName() : dto.getUsername());
        user.setRoleId(3L); // USER 角色
        user.setStatus("ACTIVE");
        sysUserMapper.insert(user);
        log.info("用户注册成功, username={}", dto.getUsername());
        return ApiResponse.success(null);
    }

    /**
     * 刷新 Token — POST /api/v1/auth/refresh
     */
    @PostMapping("/refresh")
    public ApiResponse<LoginVO> refresh(@Valid @RequestBody RefreshTokenDTO dto) {
        LoginVO vo = authService.refresh(dto);
        return ApiResponse.success(vo);
    }

    @Data
    public static class RegisterDTO {
        private String username;
        private String password;
        private String realName;
        private String captchaCode;
        private String captchaKey;
    }
}
