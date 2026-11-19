package com.whm.starbucks.dao;

import com.whm.starbucks.pojo.entity.Orders;
import org.apache.ibatis.annotations.Param;
import com.whm.starbucks.pojo.entity.Product;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository {
    Product selectProductByName(@Param("name") String name);

    List<Product> selectProductByCategory(@Param("category") String category);

    Product selectProductAvailable(@Param("id") Long id);

    List<Product> selectProductsByIds(@Param("ids") List<Long> ids);

    List<Product> selectProductsByCategory(@Param("category") Long categoryId);

    List<Product> selectProductsByStore(@Param("storeId") Long storeId);
}

