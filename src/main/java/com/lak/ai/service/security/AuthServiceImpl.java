package com.lak.ai.service.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lak.ai.common.exception.AuthException;
import com.lak.ai.mapper.SysUserMapper;
import com.lak.ai.model.dto.LoginDTO;
import com.lak.ai.model.dto.RefreshTokenDTO;
import com.lak.ai.model.entity.SysUser;
import com.lak.ai.model.vo.LoginVO;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final JwtService jwtService;
    private final CaptchaService captchaService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    @Override
    public LoginVO login(LoginDTO dto) {
        // 1. 验证码校验
        if (!captchaService.validate(dto.getCaptchaKey(), dto.getCaptchaCode())) {
            throw new AuthException(1_004, "验证码错误或已过期");
        }

        // 2. 查询用户
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (user == null) {
            throw new AuthException(1_002, "用户名或密码错误");
        }

        // 3. 密码校验
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new AuthException(1_002, "用户名或密码错误");
        }

        // 4. 状态校验
        if ("DISABLED".equals(user.getStatus()) || "LOCKED".equals(user.getStatus())) {
            throw new AuthException(1_003, "账户已被禁用，请联系管理员");
        }

        // 5. 签发 Token
        List<String> roles = List.of("USER"); // 简化——后续从 sys_role 关联查询
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getUsername(), roles);
        String refreshToken = jwtService.generateRefreshToken(user.getId(), user.getUsername());

        log.info("登录成功, userId={}, username={}", user.getId(), user.getUsername());
        return LoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getAccessExpirationSeconds())
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .roles(roles)
                .build();
    }

    @Override
    public LoginVO refresh(RefreshTokenDTO dto) {
        Claims claims;
        try {
            claims = jwtService.parseToken(dto.getRefreshToken());
        } catch (Exception e) {
            throw new AuthException(1_007, "Refresh Token 无效");
        }
        if (!jwtService.isRefreshToken(claims)) {
            throw new AuthException(1_007, "Refresh Token 无效");
        }

        // 检查 Token 是否已被废弃
        if (isTokenRevoked(dto.getRefreshToken())) {
            throw new AuthException(1_008, "Token 已被废弃");
        }

        Long userId = claims.get("userId", Long.class);
        String username = claims.getSubject();

        // 查询用户确认状态
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || "DISABLED".equals(user.getStatus()) || "LOCKED".equals(user.getStatus())) {
            throw new AuthException(1_003, "账户已被禁用");
        }

        // 废弃旧 Refresh Token（防重放攻击）
        revokeToken(dto.getRefreshToken(), claims);

        List<String> roles = List.of("USER");
        String accessToken = jwtService.generateAccessToken(userId, username, roles);
        String refreshToken = jwtService.generateRefreshToken(userId, username);

        return LoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtService.getAccessExpirationSeconds())
                .userId(userId)
                .username(username)
                .realName(user.getRealName())
                .roles(roles)
                .build();
    }

    private boolean isTokenRevoked(String token) {
        String hash = tokenHash(token);
        return Boolean.TRUE.equals(redisTemplate.hasKey("token:revoked:" + hash));
    }

    private void revokeToken(String token, Claims claims) {
        String hash = tokenHash(token);
        // TTL 等于 Token 剩余有效时间
        long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
        if (remaining > 0) {
            redisTemplate.opsForValue().set("token:revoked:" + hash, "1",
                    Duration.ofMillis(remaining));
        }
    }

    private String tokenHash(String token) {
        // 取 token 后 16 字节做 SHA-256 摘要，避免 Redis Key 过长
        String suffix = token.length() > 32 ? token.substring(token.length() - 32) : token;
        return Integer.toHexString(suffix.hashCode());
    }
}
