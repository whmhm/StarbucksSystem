package com.whm.starbucks.pojo.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付结果。
 *
 * 为什么要回传 orderStatus 而不只回传支付结果：
 * 前端点击「支付」后需要立刻刷新订单页，如果只给 payStatus，还得再发一次订单详情请求，
 * 用户体验上是两次往返；这里把订单最新状态一起带回去。
 *
 * 为什么要回传 idempotent（是否命中幂等）：
 * 便于前端区分「首次支付成功」和「重复提交，返回的是上次结果」，
 * 排查对账问题时这也是最有用的一条线索。
 */
@Data
public class PaymentVo {

    /** 支付流水主键 */
    private Long paymentId;

    private Long orderId;

    /** 订单业务号，对账时按这个查 */
    private String orderNo;

    /** 支付渠道 */
    private String payChannel;

    /** 实付金额（取自 order.payAmount，不是前端传的） */
    private BigDecimal payAmount;

    /** 渠道流水号 = 幂等键 */
    private String transactionNo;

    /** 流水状态：0-待支付 1-支付成功 2-支付失败 3-已退款 */
    private Integer payStatus;

    /** 支付后订单的最新状态（正常应为 1 已支付/待接单） */
    private Integer orderStatus;

    /** 本次请求是否命中了幂等（true = 之前已经处理过，本次未重复扣款） */
    private boolean idempotent;
}
