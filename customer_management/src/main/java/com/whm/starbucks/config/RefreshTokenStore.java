package com.whm.starbucks.config;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RefreshTokenStore {

    private final StringRedisTemplate redisTemplate;
    private static final String PREFIX = "auth:rt:";

    public void store(Long userId, String jti, long ttlMillis) {
        redisTemplate.opsForValue().set(
                PREFIX + userId + ":" + jti, "1",
                Duration.ofMillis(ttlMillis));
    }

    public boolean isValid(Long userId, String jti) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(PREFIX + userId + ":" + jti));
    }

    public void revoke(Long userId, String jti) {
        redisTemplate.delete(PREFIX + userId + ":" + jti);
    }

    // 踢人/挂失：撤销该用户所有 Refresh Token
    public void revokeAll(Long userId) {
        Set<String> keys = redisTemplate.keys(PREFIX + userId + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }
}