package com.whm.starbucks.pojo.entity;

import lombok.Data;

import java.math.BigDecimal;
@Data
public class Store {
    private Long id;

    private String storeCode;
    private String storeName;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String address;

    private String businessHours;

    private Integer storeStatus;

    private Integer isDeleted;

    private String gmtCreate;
    private String gmtModified;
}
