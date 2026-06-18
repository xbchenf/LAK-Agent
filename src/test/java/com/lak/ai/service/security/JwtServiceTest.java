package com.lak.ai.service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        // 使用固定密钥确保测试可重复
        jwtService = new JwtService(
                "test-secret-key-for-jwt-testing-must-be-32-bytes!!",
                7200,   // access: 2h
                604800  // refresh: 7d
        );
    }

    @Test
    void shouldGenerateAndParseValidAccessToken() {
        String token = jwtService.generateAccessToken(1L, "admin", List.of("ADMIN", "USER"));

        Claims claims = jwtService.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo("admin");
        assertThat(claims.get("userId", Long.class)).isEqualTo(1L);
        assertThat(claims.getIssuer()).isEqualTo("lak-ai-platform");
        assertThat(claims.get("roles", List.class)).contains("ADMIN", "USER");
        assertThat(claims.getExpiration()).isAfter(new java.util.Date());
    }

    @Test
    void shouldGenerateRefreshTokenWithTypeClaim() {
        String token = jwtService.generateRefreshToken(1L, "admin");

        Claims claims = jwtService.parseToken(token);
        assertThat(jwtService.isRefreshToken(claims)).isTrue();
        assertThat(claims.get("type", String.class)).isEqualTo("refresh");
    }

    @Test
    void accessTokenShouldNotBeRefreshType() {
        String token = jwtService.generateAccessToken(1L, "admin", List.of("USER"));

        Claims claims = jwtService.parseToken(token);
        assertThat(jwtService.isRefreshToken(claims)).isFalse();
    }

    @Test
    void shouldRejectTokenWithWrongSecret() {
        String token = jwtService.generateAccessToken(1L, "admin", List.of("USER"));

        JwtService otherService = new JwtService(
                "different-secret-key-for-testing-only!!", 7200, 604800);

        assertThatThrownBy(() -> otherService.parseToken(token))
                .isInstanceOf(Exception.class);
    }

    @Test
    void shouldReturnCorrectExpirationSeconds() {
        assertThat(jwtService.getAccessExpirationSeconds()).isEqualTo(7200);
    }

    @Test
    void shouldRejectMalformedToken() {
        assertThatThrownBy(() -> jwtService.parseToken("not.a.valid.token"))
                .isInstanceOf(Exception.class);
    }
}
