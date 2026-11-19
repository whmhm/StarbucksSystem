package com.whm.starbucks;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.Duration;

@RunWith(SpringRunner.class)
@SpringBootTest
public class RedisConnectionTest {
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    public void testConnection() {
        redisTemplate.opsForValue().set("test:ping", "pong", Duration.ofSeconds(10));
        assert "pong".equals(redisTemplate.opsForValue().get("test:ping"));
    }
}