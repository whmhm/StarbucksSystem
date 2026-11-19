package com.whm.starbucks.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductVo {
    private Long id;

    private Long categoryId;

    private String productName;

    private String description;

    private BigDecimal basePrice;

    private String imageUrl;

    private Integer isAvailable;

    private Integer isDeleted;

    private BigDecimal storePrice;   // 门店价，来自 store_product.store_price

    private Integer stockQuantity;   // 门店库存，来自 store_product.stock_quantity
}
