package com.whm.starbucks.pojo.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItem {
    private Long id;

    private Long orderId;

    private Long productId;

    private String productName;

    private Integer quantity;

    private BigDecimal unitPrice;

    private BigDecimal customizationPrice;

    private String customizationSnapshot;

    private BigDecimal itemTotalPrice;

    private Integer isDeleted;

    private String gmtCreate; // '创建时间'

    private String gmtModified; // '修改时间'

}
