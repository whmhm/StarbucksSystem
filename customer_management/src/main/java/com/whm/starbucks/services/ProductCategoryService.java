package com.whm.starbucks.services;

import com.whm.starbucks.dao.ProductCategoryRepository;
import com.whm.starbucks.pojo.entity.ProductCategory;
import com.whm.starbucks.pojo.vo.ProductCategoryVo;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProductCategoryService {
    @Resource
    private ProductCategoryRepository productCategoryRepository;
    public List<ProductCategoryVo> getProductCategory() {
        List<ProductCategory> productCategories = productCategoryRepository.getCategoryAll();
        List<ProductCategoryVo> productCategoryVoList = new ArrayList<>();
        for (ProductCategory productCategory : productCategories) {
            ProductCategoryVo productCategoryVo = new ProductCategoryVo();
            productCategoryVo.setId(productCategory.getId());
            productCategoryVo.setCategoryName(productCategory.getCategoryName());
            productCategoryVo.setSortOrder(productCategory.getSortOrder());
            productCategoryVoList.add(productCategoryVo);
        }
        return productCategoryVoList;
    }
}
