package com.whm.starbucks.controller;

import com.whm.starbucks.pojo.dto.PaymentDto;
import com.whm.starbucks.pojo.vo.PaymentVo;
import com.whm.starbucks.pojo.vo.ResponseVo;
import com.whm.starbucks.services.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付接口。
 *
 * 路由挂在 /payment 下而不是 /order 下：
 * 支付是有自己生命周期的领域（流水、渠道、对账、退款），
 * 后续接真实渠道时只需要换 PaymentService 的实现，Controller 和前端契约都不用动。
 *
 * 鉴权：所有接口都从 @AuthenticationPrincipal 取 userId，
 * 请求体里没有、也不接受 userId —— 否则任何人都能替别人付款/退款。
 */
@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 发起支付。
     *
     * 请求示例：
     *   POST /payment/pay
     *   Authorization: Bearer <access_token>
     *   { "orderId": 12, "payChannel": "MOCK", "transactionNo": "MOCK20260101120000123" }
     *
     * 幂等约定：同一笔支付重试时，transactionNo 必须传同一个值；
     * 不传则由服务端生成——那种情况下重复点击会产生新的流水号，
     * 只能靠「该订单已支付」这一层兜底，因此前端重试时建议自己带上 transactionNo。
     */
    @PostMapping("/pay")
    public ResponseVo<PaymentVo> pay(@AuthenticationPrincipal Long userId,
                                     @RequestBody PaymentDto paymentDto) {
        PaymentVo paymentVo = paymentService.pay(userId, paymentDto);
        return ResponseVo.success("支付成功", paymentVo);
    }

    /**
     * 退款（Mock）。orderId 用 query 参数而不是请求体，
     * 因为这是「对某笔订单的幂等操作」，参数越少越不容易被构造。
     *
     * 真实业务里退款通常还需要权限（只有运营/店长能发起），
     * 本项目当前所有登录用户都是 ROLE_USER，接入 roles 后要在 SecurityConfig 补 .antMatchers("/payment/refund").hasRole("ADMIN")。
     */
    @PostMapping("/refund")
    public ResponseVo<PaymentVo> refund(@AuthenticationPrincipal Long userId,
                                        @RequestParam Long orderId) {
        PaymentVo paymentVo = paymentService.refund(userId, orderId);
        return ResponseVo.success("退款成功", paymentVo);
    }
}
