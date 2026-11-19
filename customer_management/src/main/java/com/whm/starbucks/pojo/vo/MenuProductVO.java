package com.whm.starbucks.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

// 产品节点（已合并门店价和门店可售状态）
@Data
public class MenuProductVO {
    private Long productId;

    private String productName;

    private String description;

    private String imageUrl;

    private BigDecimal price;               // 门店价，来自 store_product.store_price

    private Integer stockQuantity;          // 门店库存

    private Integer isAvailable;            // 门店是否可售

    private List<CustomizationGroupVO> customizationGroups;  // 该产品支持的客制化
}