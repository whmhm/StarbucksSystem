package com.whm.starbucks.services;

import com.whm.starbucks.dao.ProductRepository;
import com.whm.starbucks.pojo.entity.Product;
import com.whm.starbucks.pojo.vo.ProductVo;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
public class ProductService {
    @Resource
    private ProductRepository productRepository;

    public ProductVo selectProductAvailable(Long productId) {
        Product product = productRepository.selectProductAvailable(productId);
        if (product == null) {
            return null;
        }
        ProductVo productVo = new ProductVo();
        productVo.setId(product.getId());
        productVo.setCategoryId(product.getCategoryId());
        productVo.setProductName(product.getProductName());
        productVo.setDescription(product.getDescription());
        productVo.setBasePrice(product.getBasePrice());
        productVo.setImageUrl(product.getImageUrl());
        productVo.setIsAvailable(product.getIsAvailable());
        productVo.setIsDeleted(product.getIsDeleted());
        return productVo;
    }

    public List<ProductVo> selectProductsByIds(List<Long> productIds) {
        List<Product> products = productRepository.selectProductsByIds(productIds);
        List<ProductVo> productVos = new ArrayList<>();
        products.forEach(product -> {
            ProductVo productVo = new ProductVo();
            productVo.setId(product.getId());
            productVo.setCategoryId(product.getCategoryId());
            productVo.setProductName(product.getProductName());
            productVo.setDescription(product.getDescription());
            productVo.setBasePrice(product.getBasePrice());
            productVo.setImageUrl(product.getImageUrl());
            productVo.setIsAvailable(product.getIsAvailable());
            productVos.add(productVo);
        });
        return productVos;
    }
    // 根据分类查询商品
    public List<ProductVo> selectProductsByCategory(Long categoryId) {
        List<Product> products = productRepository.selectProductsByCategory(categoryId);
        List<ProductVo> productVos = new ArrayList<>();
        products.forEach(product -> {
            ProductVo productVo = new ProductVo();
            productVo.setId(product.getId());
            productVo.setCategoryId(product.getCategoryId());
            productVo.setProductName(product.getProductName());
            productVo.setDescription(product.getDescription());
            productVo.setBasePrice(product.getBasePrice());
            productVo.setImageUrl(product.getImageUrl());
            productVo.setIsAvailable(product.getIsAvailable());
            productVos.add(productVo);
        });
        return productVos;
    }

//    查询门店可售商品
    public List<ProductVo> getProductsByStore(Long storeId) {
        List<Product> products = productRepository.selectProductsByStore(storeId);

        List<ProductVo> productVos = new ArrayList<>();
        products.forEach(product -> {
            ProductVo productVo = new ProductVo();
            productVo.setId(product.getId());
            productVo.setCategoryId(product.getCategoryId());
            productVo.setProductName(product.getProductName());
            productVo.setDescription(product.getDescription());
            productVo.setBasePrice(product.getBasePrice());
            productVo.setImageUrl(product.getImageUrl());
            productVo.setIsAvailable(product.getIsAvailable());
            productVo.setStorePrice(product.getStorePrice());
            productVo.setStockQuantity(product.getStockQuantity());
            productVos.add(productVo);
        });
        return productVos;
    }
}
