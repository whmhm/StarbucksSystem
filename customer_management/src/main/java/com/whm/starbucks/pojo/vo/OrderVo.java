package com.whm.starbucks.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderVo {
    private Long id;

    private String orderNo;

    private String userPhone;

    private Long storeId;

    private Integer fulfillmentType;

    private Integer orderStatus; // '订单状态：0-待支付 1-已支付/待接单 2-制作中 3-待取餐/配送中 4-已完成 5-已取消 6-已退款'

    private BigDecimal totalAmount; // '商品总金额'

    private BigDecimal discountAmount; // '优惠总金额'

    private BigDecimal payAmount; // '实付金额'

    private String pickupCode; // '取餐码/口令'

    private String estimatedReadyTime; // '预计完成/送达时间'

    private String remark; // '订单备注'


    private Integer isDeleted; // '是否删除：0-否 1-是'

    private String gmtCreate; // '创建时间'

    private String gmtModified; // '修改时间'

    private List<OrderItemVo> items; // 订单明细

}
