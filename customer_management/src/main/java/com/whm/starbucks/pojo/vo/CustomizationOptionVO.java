package com.whm.starbucks.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class CustomizationOptionVO {
    private Long optionId;
    private String optionName;
    private BigDecimal extraPrice;
}