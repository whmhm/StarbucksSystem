package com.whm.starbucks.pojo.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StoreDto {
    private Long storeId;

    private String storeName;

    private BigDecimal latitude;

    private BigDecimal longitude;
}
