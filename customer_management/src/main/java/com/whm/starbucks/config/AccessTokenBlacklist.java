package com.whm.starbucks.config;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AccessTokenBlacklist {
    private static final String PREFIX = "auth:bl:";

    private final StringRedisTemplate redisTemplate;

    public void revoke(String jti, long ttlMillis) {
        if (jti == null || ttlMillis <= 0)
            return;

        redisTemplate.opsForValue().set(PREFIX + jti, "1", Duration.ofMillis(ttlMillis));

    }

    public boolean isRevoked(String jti) {
        return jti != null && Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + jti));
    }

}
