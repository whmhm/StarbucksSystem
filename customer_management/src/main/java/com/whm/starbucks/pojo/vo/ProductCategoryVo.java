package com.whm.starbucks.pojo.vo;

import lombok.Data;

@Data
public class ProductCategoryVo {
    private Long id;

    private String categoryName;

    private Integer sortOrder;
}
