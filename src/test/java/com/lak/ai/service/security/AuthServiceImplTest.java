package com.lak.ai.service.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lak.ai.common.exception.AuthException;
import com.lak.ai.mapper.SysUserMapper;
import com.lak.ai.model.dto.LoginDTO;
import com.lak.ai.model.dto.RefreshTokenDTO;
import com.lak.ai.model.entity.SysUser;
import com.lak.ai.model.vo.LoginVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

    @Mock private SysUserMapper sysUserMapper;
    @Mock private JwtService jwtService;
    @Mock private CaptchaService captchaService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authService = new AuthServiceImpl(
                sysUserMapper, jwtService, captchaService, passwordEncoder, redisTemplate);
    }

    // ===== Login Tests =====

    @Test
    void shouldLoginSuccessfully_whenValidCredentials() {
        LoginDTO dto = buildLoginDTO("admin", "pass123", "key1", "1234");
        SysUser user = buildUser(1L, "admin", "管理员", "ACTIVE");
        user.setPassword("$2a$10$hashed");

        when(captchaService.validate("key1", "1234")).thenReturn(true);
        when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("pass123", user.getPassword())).thenReturn(true);
        when(jwtService.generateAccessToken(eq(1L), eq("admin"), anyList()))
                .thenReturn("access-token-xxx");
        when(jwtService.generateRefreshToken(1L, "admin"))
                .thenReturn("refresh-token-xxx");
        when(jwtService.getAccessExpirationSeconds()).thenReturn(7200L);

        LoginVO result = authService.login(dto);

        assertThat(result.getAccessToken()).isEqualTo("access-token-xxx");
        assertThat(result.getRefreshToken()).isEqualTo("refresh-token-xxx");
        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getUsername()).isEqualTo("admin");
    }

    @Test
    void shouldRejectLogin_whenCaptchaInvalid() {
        LoginDTO dto = buildLoginDTO("admin", "pass123", "key1", "wrong");
        when(captchaService.validate("key1", "wrong")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("验证码")
                .extracting("code").isEqualTo(1_004);
    }

    @Test
    void shouldRejectLogin_whenUsernameNotFound() {
        LoginDTO dto = buildLoginDTO("ghost", "pass123", "key1", "1234");
        when(captchaService.validate("key1", "1234")).thenReturn(true);
        when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(AuthException.class)
                .extracting("code").isEqualTo(1_002);
    }

    @Test
    void shouldRejectLogin_whenPasswordMismatch() {
        LoginDTO dto = buildLoginDTO("admin", "wrongpass", "key1", "1234");
        SysUser user = buildUser(1L, "admin", "", "ACTIVE");

        when(captchaService.validate("key1", "1234")).thenReturn(true);
        when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("wrongpass", user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(AuthException.class)
                .extracting("code").isEqualTo(1_002);
    }

    @Test
    void shouldRejectLogin_whenAccountDisabled() {
        LoginDTO dto = buildLoginDTO("admin", "pass123", "key1", "1234");
        SysUser user = buildUser(1L, "admin", "", "DISABLED");

        when(captchaService.validate("key1", "1234")).thenReturn(true);
        when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("pass123", user.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(AuthException.class)
                .extracting("code").isEqualTo(1_003);
    }

    @Test
    void shouldRejectLogin_whenAccountLocked() {
        LoginDTO dto = buildLoginDTO("admin", "pass123", "key1", "1234");
        SysUser user = buildUser(1L, "admin", "", "LOCKED");

        when(captchaService.validate("key1", "1234")).thenReturn(true);
        when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("pass123", user.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(dto))
                .isInstanceOf(AuthException.class)
                .extracting("code").isEqualTo(1_003);
    }

    // ===== Refresh Tests =====

    @Test
    void shouldRefreshSuccessfully_whenValidRefreshToken() {
        RefreshTokenDTO dto = new RefreshTokenDTO();
        dto.setRefreshToken("old-refresh-token");
        SysUser user = buildUser(1L, "admin", "管理员", "ACTIVE");

        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(claims.get("type", String.class)).thenReturn("refresh");
        when(claims.get("userId", Long.class)).thenReturn(1L);
        when(claims.getSubject()).thenReturn("admin");
        when(claims.getExpiration()).thenReturn(new java.util.Date(System.currentTimeMillis() + 600_000));

        when(jwtService.parseToken("old-refresh-token")).thenReturn(claims);
        when(jwtService.isRefreshToken(claims)).thenReturn(true);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(sysUserMapper.selectById(1L)).thenReturn(user);
        when(jwtService.generateAccessToken(eq(1L), eq("admin"), anyList()))
                .thenReturn("new-access-token");
        when(jwtService.generateRefreshToken(1L, "admin"))
                .thenReturn("new-refresh-token");
        when(jwtService.getAccessExpirationSeconds()).thenReturn(7200L);

        LoginVO result = authService.refresh(dto);

        assertThat(result.getAccessToken()).isEqualTo("new-access-token");
        assertThat(result.getRefreshToken()).isEqualTo("new-refresh-token");
        // 验证旧 Token 被废弃
        verify(valueOperations).set(contains("token:revoked:"), eq("1"), any(Duration.class));
    }

    @Test
    void shouldRejectRefresh_whenTokenAlreadyRevoked() {
        RefreshTokenDTO dto = new RefreshTokenDTO();
        dto.setRefreshToken("stolen-token");

        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(claims.get("type", String.class)).thenReturn("refresh");
        when(jwtService.parseToken("stolen-token")).thenReturn(claims);
        when(jwtService.isRefreshToken(claims)).thenReturn(true);
        when(redisTemplate.hasKey(anyString())).thenReturn(true); // 已被废弃

        assertThatThrownBy(() -> authService.refresh(dto))
                .isInstanceOf(AuthException.class)
                .extracting("code").isEqualTo(1_008);
    }

    @Test
    void shouldRejectRefresh_whenUserDisabled() {
        RefreshTokenDTO dto = new RefreshTokenDTO();
        dto.setRefreshToken("refresh-token");
        SysUser user = buildUser(1L, "admin", "", "DISABLED");

        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(claims.get("type", String.class)).thenReturn("refresh");
        when(claims.get("userId", Long.class)).thenReturn(1L);
        when(jwtService.parseToken("refresh-token")).thenReturn(claims);
        when(jwtService.isRefreshToken(claims)).thenReturn(true);
        when(redisTemplate.hasKey(anyString())).thenReturn(false);
        when(sysUserMapper.selectById(1L)).thenReturn(user);

        assertThatThrownBy(() -> authService.refresh(dto))
                .isInstanceOf(AuthException.class)
                .extracting("code").isEqualTo(1_003);
    }

    // ===== Helpers =====

    private LoginDTO buildLoginDTO(String username, String password, String captchaKey, String captchaCode) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(username);
        dto.setPassword(password);
        dto.setCaptchaKey(captchaKey);
        dto.setCaptchaCode(captchaCode);
        return dto;
    }

    private SysUser buildUser(Long id, String username, String realName, String status) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setRealName(realName);
        user.setStatus(status);
        user.setPassword("$2a$10$hashed");
        return user;
    }
}
