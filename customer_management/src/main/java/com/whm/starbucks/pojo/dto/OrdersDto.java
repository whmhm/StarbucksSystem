package com.whm.starbucks.pojo.dto;

import lombok.Data;

import java.util.List;

/**
 * 下单请求。
 * 注意：userId 从 JWT 取；userPhone、totalAmount、discountAmount、payAmount、pickupCode
 * 全部由服务端生成或计算，不接受客户端传入（多传的字段会被 Jackson 忽略）。
 */
@Data
public class OrdersDto {

    private Long storeId;

    private Integer fulfillmentType; // '履约类型：1-啡快自取 2-专星送外卖 3-堂食'

    private String remark; // '订单备注'

    private List<OrderItemDto> items;  // 该订单的产品列表

}
