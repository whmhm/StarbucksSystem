package com.whm.starbucks.pojo.dto;

import lombok.Data;

/**
 * 支付请求。
 *
 * 字段取舍说明：
 *   - orderId：必传，支付哪一笔订单；
 *   - payChannel：支付渠道，Mock 阶段固定传 MOCK，保留字段是为了以后接真渠道不用改接口；
 *   - transactionNo（幂等键）：客户端可以自己生成并传入（同一笔支付重试时复用同一个值），
 *     不传则由服务端生成。两条路径都必须保证同一个逻辑支付只产生一条成功流水。
 *
 * 刻意不接收 payAmount：
 *   金额一律以 orders.pay_amount 为准，前端传多少都不采信。
 *   否则前端把 38.00 改成 0.01 就能 1 分钱买咖啡——这是支付接口最经典的漏洞。
 */
@Data
public class PaymentDto {

    /** 订单主键 */
    private Long orderId;

    /** 支付渠道：MOCK / WECHAT / ALIPAY / BALANCE，为空时按 MOCK 处理 */
    private String payChannel;

    /** 幂等键（渠道流水号）。同一笔支付的重试必须复用同一个值 */
    private String transactionNo;
}
