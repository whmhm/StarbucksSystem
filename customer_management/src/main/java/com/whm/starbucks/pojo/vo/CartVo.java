package com.whm.starbucks.pojo.vo;

import com.whm.starbucks.pojo.dto.CartItemDto;
import lombok.Data;

import java.util.List;

@Data
public class CartVo {
    private Long id;

    private Long userId;

    private Long storeId;

    private List<CartItemVo> cartItemList;
}
