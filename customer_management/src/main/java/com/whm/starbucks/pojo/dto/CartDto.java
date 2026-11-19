package com.whm.starbucks.pojo.dto;

import lombok.Data;

import java.util.List;

@Data
public class CartDto {
    private Long userId;

    private Long storeId;

    private List<CartItemDto> cartItemList;

}
