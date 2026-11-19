package com.whm.starbucks.pojo.entity;

import lombok.Data;

@Data
public class ProductCategory {
    private Long id;
    private String categoryName; // '分类名称，如 咖啡、茶瓦纳、糕点',
    private Integer sortOrder; // '排序权重，越小越靠前',
    private Integer isDeleted;
    private String gmtCreate;
    private String gmtModified;
}