package com.whm.starbucks.dao;

import org.apache.ibatis.annotations.Param;
import com.whm.starbucks.pojo.entity.Store;

import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoreRepository {

    Store findByStoreId(@Param("storeId") Long storeId);
    List<Store> findByStoreIds(@Param("storeIds") List<Long> storeIds);

    List<Store> findAll();

    Long updateStoreLocation(@Param("storeId") Long storeId, @Param("longitude") Double longitude, @Param("latitude") Double latitude);

}
