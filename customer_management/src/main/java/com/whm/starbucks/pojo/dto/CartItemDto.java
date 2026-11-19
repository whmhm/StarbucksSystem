package com.whm.starbucks.pojo.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CartItemDto {
    private Long cartId;
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal customizationPrice;
    private String customizationSnapshot;
}
