package com.whm.starbucks.dao;

import com.whm.starbucks.pojo.entity.ProductCategory;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductCategoryRepository {
    List<ProductCategory> getCategoryAll();

}
