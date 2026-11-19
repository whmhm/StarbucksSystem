package com.whm.starbucks.config;

import com.whm.starbucks.dao.StoreRepository;
import com.whm.starbucks.pojo.entity.Store;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class StoreGeoLoader implements CommandLineRunner {
    /**
     * 创建 SpringApplication，准备环境。
     *
     * 创建 ApplicationContext，扫描并实例化所有 Bean（包括你的 StoreGeoLoader、StoreMapper、StringRedisTemplate）。
     *
     * 执行所有 ApplicationRunner 和 CommandLineRunner 的 run 方法。你的 StoreGeoLoader 就在这一步执行。
     */
    private final StringRedisTemplate redisTemplate;
    private final StoreRepository storeRepository;

    @Override
    public void run(String... args) throws Exception {
        // Load store geo data here
        String key = "store:geo";
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return;
        }
        List<Store> storeList = storeRepository.findAll();
        if (storeList.isEmpty()) return;
        try {
            List<RedisGeoCommands.GeoLocation<String>> locations = storeList.stream().map(e ->
                    new RedisGeoCommands.GeoLocation<>(e.getId().toString(), new Point(e.getLongitude().doubleValue(), e.getLatitude().doubleValue()))
                    ).collect(Collectors.toList());

            redisTemplate.opsForGeo().add(key, locations);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
