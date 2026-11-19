package com.whm.starbucks.services;

import com.whm.starbucks.dao.StoreRepository;
import com.whm.starbucks.exceptions.BizException;
import com.whm.starbucks.pojo.dto.StoreDto;
import com.whm.starbucks.pojo.entity.Store;
import com.whm.starbucks.pojo.vo.StoreVo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoreService {
    // 通过storeId获取店铺信息
    @Resource
    private StoreRepository storeRepository;

    private final StringRedisTemplate redisTemplate;

    private static final String GEO_KEY = "store:geo";


    public StoreVo getStoreByStoreId(Long storeId) {
        Store store = storeRepository.findByStoreId(storeId);
        if (store == null) return null;

        StoreVo storeVo = new StoreVo();
        storeVo.setStoreId(store.getId());
        storeVo.setStoreCode(store.getStoreCode());
        storeVo.setStoreName(store.getStoreName());
        storeVo.setLatitude(store.getLatitude());
        storeVo.setLongitude(store.getLongitude());
        storeVo.setAddress(store.getAddress());
        storeVo.setBusinessHours(store.getBusinessHours());
        storeVo.setStoreStatus(store.getStoreStatus());

        return storeVo;

    }

//    查询门店可售

    public List<StoreVo> getStoreAll() {
        List<Store> stores = storeRepository.findAll();
        List<StoreVo> storeVos = stores.stream().map(store -> {
            StoreVo storeVo = new StoreVo();
            storeVo.setStoreId(store.getId());
            storeVo.setStoreCode(store.getStoreCode());
            storeVo.setStoreName(store.getStoreName());
            storeVo.setLatitude(store.getLatitude());
            storeVo.setLongitude(store.getLongitude());
            storeVo.setAddress(store.getAddress());
            storeVo.setBusinessHours(store.getBusinessHours());
            storeVo.setStoreStatus(store.getStoreStatus());
            return storeVo;
        }).collect(Collectors.toList());
        return storeVos;
    }
    public List<StoreVo> getNearbyStores(
            Double longitude, Double latitude, Double radiusKm
    ) {
        // 用户没有提供经纬度 查全部的
        if(longitude == null || latitude == null) {
            return storeRepository.findAll().stream().map(this::toVOWithoutDistance).collect(Collectors.toList());
        }
        // 用户提供 构造圆心 + 半径
        Point center = new Point(longitude, latitude);
        Distance distance = new Distance(radiusKm, Metrics.KILOMETERS);
        Circle circle = new Circle(center, distance);
        // 查询参数 带距离 距离升序
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeDistance()
                .sortAscending()
                .limit(20);

        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redisTemplate.opsForGeo().radius(GEO_KEY,circle,args);
        if (results == null || results.getContent().isEmpty()) {

            return Collections.emptyList();
        }
        List<Long> storeIds = new ArrayList<>();
        Map<Long, Double> distanceMap = new HashMap<>();
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> result : results.getContent()) {
            Long storeId = Long.valueOf(result.getContent().getName());
            storeIds.add(storeId);
            distanceMap.put(storeId, result.getDistance().getValue());
        }
        List<Store> stores = storeRepository.findByStoreIds(storeIds);
        Map<Long, Store> storeMap = stores.stream()
                .collect(Collectors.toMap(Store::getId, s -> s));
        return storeIds.stream()
                .filter(storeMap::containsKey)
                .map(id -> {
                    Store s = storeMap.get(id);
                    StoreVo vo = new StoreVo();
                    vo.setStoreId(s.getId());
                    vo.setStoreName(s.getStoreName());
                    vo.setAddress(s.getAddress());
                    vo.setStoreStatus(s.getStoreStatus());
                    vo.setBusinessHours(s.getBusinessHours());
                    return vo;
                })
                .collect(Collectors.toList());

    }
    @Transactional
    public void updateStoreLocation(StoreDto storeDto) {
        // 新增/更新商店坐标时 把坐标写入redis

        // 1、存mysql 2、更新redis
        Long result = storeRepository.updateStoreLocation(storeDto.getStoreId(), storeDto.getLatitude().doubleValue(), storeDto.getLongitude().doubleValue());
        if (result == 0) {
            throw new BizException(500,"更新门店坐标失败");

        }
        redisTemplate.opsForGeo().add(GEO_KEY,
                new Point(storeDto.getLatitude().doubleValue(),
                        storeDto.getLongitude().doubleValue()),
                storeDto.getStoreId().toString());

    }

    public StoreVo toVOWithoutDistance(Store store) {
        StoreVo s = new StoreVo();

        s.setStoreId(store.getId());
        s.setStoreCode(store.getStoreCode());
        s.setStoreName(store.getStoreName());
        s.setLatitude(store.getLatitude());
        s.setLongitude(store.getLongitude());
        s.setAddress(store.getAddress());
        s.setBusinessHours(store.getBusinessHours());
        s.setStoreStatus(store.getStoreStatus());
        return s;
    }

}
