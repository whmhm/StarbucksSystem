package com.whm.starbucks.pojo.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class Product {
    private Long id;

    private Long categoryId;

    private String productName; // 商品名称

    private String description;

    private BigDecimal basePrice;

    private String imageUrl;

    private Integer isAvailable;

    private Integer isDeleted;

    // 以下两个字段不来自 product 表，由 store_product 联表查询填充
    private BigDecimal storePrice;   // 门店价

    private Integer stockQuantity;   // 门店库存

    private String gmtCreate;

    private String gmtModified;
}
