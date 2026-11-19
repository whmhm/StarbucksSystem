package com.whm.starbucks.pojo.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemDto {

    private Long orderId;

    private Long productId;

    private Integer quantity;

    private BigDecimal unitPrice;

    private BigDecimal customizationPrice;

    private String customizationSnapshot;

    private BigDecimal itemTotalPrice;
}
