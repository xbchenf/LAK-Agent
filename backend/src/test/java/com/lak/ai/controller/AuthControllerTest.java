package com.lak.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lak.ai.common.exception.AuthException;
import com.lak.ai.common.exception.GlobalExceptionHandler;
import com.lak.ai.model.dto.LoginDTO;
import com.lak.ai.model.dto.RefreshTokenDTO;
import com.lak.ai.model.vo.LoginVO;
import com.lak.ai.service.security.AuthService;
import com.lak.ai.service.security.CaptchaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthControllerTest {

    @Mock private AuthService authService;
    @Mock private CaptchaService captchaService;
    @InjectMocks private AuthController controller;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldLoginSuccessfully() throws Exception {
        LoginDTO dto = dto("admin", "pass123", "key1", "1234");
        when(authService.login(any())).thenReturn(LoginVO.builder()
                .accessToken("at-xxx").refreshToken("rt-xxx").expiresIn(7200)
                .userId(1L).username("admin").realName("管理员")
                .roles(List.of("USER")).build());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("at-xxx"));
    }

    @Test
    void shouldFailLogin_whenMissingFields() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ad\"}"))
                .andExpect(status().isBadRequest());
    }

    // standalone MockMvc中@Valid校验在service调用前执行, AuthException由校验拦截器先处理

    @Test
    void shouldFailLogin_whenCaptchaError() throws Exception {
        LoginDTO dto = dto("admin", "pass123", "k1", "wrong");
        when(authService.login(any())).thenThrow(new AuthException(1_004, "验证码错误"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1004));
    }

    @Test
    void shouldFailLogin_whenAccountDisabled() throws Exception {
        LoginDTO dto = dto("admin", "pass123", "k1", "1234");
        when(authService.login(any())).thenThrow(new AuthException(1_003, "账户已被禁用"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1003));
    }

    @Test
    void shouldGetCaptcha() throws Exception {
        when(captchaService.generate()).thenReturn(
                new CaptchaService.CaptchaResult("key-abc", "1234"));

        mockMvc.perform(get("/api/v1/auth/captcha"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.captchaKey").value("key-abc"));
    }

    @Test
    void shouldRefreshToken() throws Exception {
        RefreshTokenDTO dto = new RefreshTokenDTO();
        dto.setRefreshToken("old-rt");
        when(authService.refresh(any())).thenReturn(LoginVO.builder()
                .accessToken("new-at").refreshToken("new-rt").expiresIn(7200)
                .userId(1L).username("admin").roles(List.of("USER")).build());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.accessToken").value("new-at"));
    }

    @Test
    void shouldFailRefresh_whenEmptyToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldFailRefresh_whenTokenRevoked() throws Exception {
        RefreshTokenDTO dto = new RefreshTokenDTO();
        dto.setRefreshToken("stolen");
        when(authService.refresh(any())).thenThrow(new AuthException(1_008, "Token 已被废弃"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1008));
    }

    private LoginDTO dto(String user, String pass, String key, String code) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername(user);
        dto.setPassword(pass);
        dto.setCaptchaKey(key);
        dto.setCaptchaCode(code);
        return dto;
    }
}
