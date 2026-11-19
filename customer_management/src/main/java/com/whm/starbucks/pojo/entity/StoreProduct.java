package com.whm.starbucks.pojo.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StoreProduct {
    private Long id;

    private Long storeId;

    private String productId; // 商品名称

    private BigDecimal storePrice;

    private Integer stockQuantity;

    private Integer isAvailable; // '是否可售：0-下架 1-上架'

    private String isDeleted; //'是否删除：0-否 1-是'

    private String gmtCreate;

    private String gmtModified;
}
