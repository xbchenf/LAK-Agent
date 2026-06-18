package com.lak.ai.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void shouldReturn401_whenNotAuthenticated() {
        ResponseEntity<?> resp = handler.handleAuthException(
                new AuthException(1_001, "未认证，请先登录"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn401_whenBadCredentials() {
        ResponseEntity<?> resp = handler.handleAuthException(
                new AuthException(1_002, "用户名或密码错误"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn403_whenAccountDisabled() {
        ResponseEntity<?> resp = handler.handleAuthException(
                new AuthException(1_003, "账户已被禁用"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldReturn400_whenCaptchaError() {
        ResponseEntity<?> resp = handler.handleAuthException(
                new AuthException(1_004, "验证码错误"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn400_whenCaptchaExpired() {
        ResponseEntity<?> resp = handler.handleAuthException(
                new AuthException(1_005, "验证码已过期"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn401_whenTokenExpired() {
        ResponseEntity<?> resp = handler.handleAuthException(
                new AuthException(1_006, "Access Token 已过期"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn401_whenRefreshTokenInvalid() {
        ResponseEntity<?> resp = handler.handleAuthException(
                new AuthException(1_007, "Refresh Token 无效"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn401_whenTokenRevoked() {
        ResponseEntity<?> resp = handler.handleAuthException(
                new AuthException(1_008, "Token 已被废弃"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldReturn429_whenRateLimited() {
        ResponseEntity<?> resp = handler.handleRateLimitException(
                new RateLimitException(2_102, "请求过于频繁"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void shouldReturnForbidden_whenSensitiveWord() {
        ResponseEntity<?> resp = handler.handleSensitiveWordException(
                new SensitiveWordException(2_101, "消息包含敏感内容"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void shouldReturnServiceUnavailable_whenModelException() {
        ResponseEntity<?> resp = handler.handleModelException(
                new ModelException(99_501, "大模型不可用"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void shouldReturn500_whenUnknownException() {
        ResponseEntity<?> resp = handler.handleUnknownException(
                new RuntimeException("unexpected"));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
