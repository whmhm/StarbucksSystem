package com.whm.starbucks.pojo.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentRecord {
    private Long id;
    private Long orderId;
    private String payChannel;
    private BigDecimal payAmount;
    private String transactionNo;
    private Integer payStatus;
    private Integer isDeleted;
    private String gmtCreate;
    private String gmtModified;

}
