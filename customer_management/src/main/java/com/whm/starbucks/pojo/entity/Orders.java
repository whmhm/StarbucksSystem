package com.whm.starbucks.pojo.entity;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class Orders {
    private Long id;

    private String orderNo; //'订单编号（业务唯一号）'

    private Long userId;

    private String userPhone;

    private Long storeId;

    private Integer fulfillmentType; // '履约类型：1-啡快自取 2-专星送外卖 3-堂食'

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

}
