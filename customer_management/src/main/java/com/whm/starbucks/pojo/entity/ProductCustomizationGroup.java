package com.whm.starbucks.pojo.entity;

import lombok.Data;

@Data
public class ProductCustomizationGroup {
    private Long id;

    private Long productId;

    private Long groupId;

    private Integer isDeleted;

    private String gmtCreate;
    private String gmtModified;
}
