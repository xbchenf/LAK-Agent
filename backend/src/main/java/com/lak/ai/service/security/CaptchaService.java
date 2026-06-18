package com.lak.ai.service.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 验证码服务 — Redis 缓存，5 分钟 TTL。
 */
@Service
@RequiredArgsConstructor
public class CaptchaService {

    private static final String CAPTCHA_PREFIX = "captcha:";
    private static final long CAPTCHA_TTL_SECONDS = 300;

    private final StringRedisTemplate redisTemplate;

    /**
     * 生成验证码并返回 captchaKey。
     *
     * @return CaptchaResult{key, code}
     */
    public CaptchaResult generate() {
        String key = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String code = String.format("%04d", new SecureRandom().nextInt(10000));
        redisTemplate.opsForValue().set(CAPTCHA_PREFIX + key, code, CAPTCHA_TTL_SECONDS, TimeUnit.SECONDS);
        return new CaptchaResult(key, code);
    }

    /**
     * 校验验证码，校验通过后立即删除（一次性使用）。
     */
    public boolean validate(String captchaKey, String captchaCode) {
        if (captchaKey == null || captchaCode == null) {
            return false;
        }
        String storedCode = redisTemplate.opsForValue().getAndDelete(CAPTCHA_PREFIX + captchaKey);
        return captchaCode.equalsIgnoreCase(storedCode);
    }

    public record CaptchaResult(String key, String code) {}
}
