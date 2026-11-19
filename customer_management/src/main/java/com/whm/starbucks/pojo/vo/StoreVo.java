package com.whm.starbucks.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StoreVo {
    private Long storeId;
    private String storeCode;
    private String storeName;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String address;
    private String businessHours;
    private Integer storeStatus;
    private String gmtCreate;
    private String gmtModified;
}
