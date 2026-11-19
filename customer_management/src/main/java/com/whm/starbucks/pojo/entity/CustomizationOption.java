package com.whm.starbucks.pojo.entity;

import lombok.Data;

import java.math.BigDecimal;
@Data
public class CustomizationOption {
    private Long id;

    private String groupId;

    private String optionName;

    private BigDecimal extraPrice;

    private Integer sortOrder;

    private Integer isDeleted;
    private String gmtCreate; // '创建时间'
    private String gmtModified; // '修改时间'
}
