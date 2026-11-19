package com.whm.starbucks.pojo.vo;

import lombok.Data;

import java.util.List;

// 分类节点
@Data
public class MenuCategoryVO {
    private Long categoryId;
    private String categoryName;
    private Integer sortOrder;
    private List<MenuProductVO> products;   // 该分类下当前门店可售的产品
}