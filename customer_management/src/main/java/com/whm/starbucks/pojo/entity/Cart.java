package com.whm.starbucks.pojo.entity;

import lombok.Data;

@Data
public class Cart {
    private Long id;

    private Long userId;

    private Long storeId;

    private Integer isDeleted;

    private String gmtCreate;

    private String gmtModified;
}
