package com.whm.starbucks.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemVo {

    private Long orderId;

    private Long productId;

    private String productName;

    private Integer quantity;

    private BigDecimal unitPrice;

    private BigDecimal customizationPrice;

    private String customizationSnapshot;

    private BigDecimal itemTotalPrice;
}