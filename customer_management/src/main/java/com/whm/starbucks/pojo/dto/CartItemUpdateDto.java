package com.whm.starbucks.pojo.dto;

import lombok.Data;

/**
 * 购物车单行操作请求（改数量 / 删单行共用）。
 *
 * 为什么用 cartItemId 而不是 productId 定位：
 * 同一商品选不同客制化（大杯/燕麦奶 与 超大杯/全脂奶）在购物车里是两行，
 * 用 productId 定位必然改错行。cartItemId 来自 /car/list 返回的明细 id，是唯一标识。
 *
 * 为什么还要 storeId：
 * 车头是 (user_id, store_id) 唯一定位的，
 * 服务端要先用 userId + storeId 查出 cart，才能用 cartId 校验这一行属不属于当前用户的车，
 * 只凭 cartItemId 是无法做越权校验的。
 *
 * quantity 只在「改数量」时使用，删除时忽略。
 */
@Data
public class CartItemUpdateDto {

    /** 门店主键，用于定位车头 */
    private Long storeId;

    /** 购物车明细主键（/car/list 返回的 cartItemList[].id） */
    private Long cartItemId;

    /** 修改后的数量，删除时可为空 */
    private Integer quantity;
}
