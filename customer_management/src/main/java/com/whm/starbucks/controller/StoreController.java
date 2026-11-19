package com.whm.starbucks.controller;

import com.whm.starbucks.pojo.dto.StoreDto;
import com.whm.starbucks.pojo.vo.ResponseVo;
import com.whm.starbucks.pojo.vo.StoreVo;
import com.whm.starbucks.services.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/store")
@RequiredArgsConstructor
public class StoreController {
    @Resource
    private StoreService storeService;

    @GetMapping("/all")
    public ResponseVo<List<StoreVo>> getAllStores() {
        List<StoreVo> storeVoList = storeService.getStoreAll();
        return ResponseVo.success("获取所有门店成功",storeVoList);
    }
    // 根据经纬度获取附近门店
    @GetMapping("/nearby")
    public ResponseVo<List<StoreVo>> getNearbyStores(@RequestParam(required = false) Double longitude,
                                  @RequestParam(required = false) Double latitude,
                                  @RequestParam(defaultValue = "5") Double radiusKm) {
        List<StoreVo> storeVoList = storeService.getNearbyStores(longitude, latitude, radiusKm);
        return ResponseVo.success("获取附近门店成功",storeVoList);
    }
    // 根据店铺ID获取店铺信息
    @GetMapping("/detail")
    public ResponseVo<StoreVo> getStoreDetail(@RequestParam Long storeId) {
        StoreVo storeVo = storeService.getStoreByStoreId(storeId);
        return ResponseVo.success("获取店铺信息成功",storeVo);
    }

}
