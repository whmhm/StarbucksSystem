package com.whm.starbucks.pojo.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartItem {
    private Long id;
    private Long cartId;
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal customizationPrice;
    private String customizationSnapshot;
    private BigDecimal itemTotalPrice;
    private String gmtCreate;
    private String gmtModified;

}
