package com.whm.starbucks.controller;

import com.whm.starbucks.pojo.dto.OrdersDto;
import com.whm.starbucks.pojo.vo.OrderVo;
import com.whm.starbucks.pojo.vo.ResponseVo;
import com.whm.starbucks.services.OrdersService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单接口。
 *
 * 修复说明（原文件的问题）：
 *   getOrdersByUserPhone / getOrderByNo 两个方法【没有 @GetMapping】，
 *   Spring 不会为它们注册路由，等于死代码；而且它们不校验订单归属，本身就是越权接口。
 *   这里已删除这两个方法，改由 getMyOrders / getOrderDetail 按 JWT 里的 userId 查询。
 *
 * 权限缺口（闭环阶段可接受，但必须知道）：
 *   accept / finishMaking / complete 是门店侧动作，当前系统只有 ROLE_USER，
 *   任何登录用户都能调。接入角色后应在 SecurityConfig 里限制为门店/运营角色，
 *   或把这三个接口整体迁到 store_management 模块。
 */
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrdersService ordersService;

    /**
     * 下单（前端直传 items 的老入口）。
     *
     * 保留原因：联调/压测时可以不经过购物车直接造单。
     * 正常业务请走 /order/addFromCart —— 让后端从购物车取商品，
     * 避免前端传来的商品列表与购物车不一致，也避免下单后购物车残留。
     */
    @PostMapping("/add")
    public ResponseVo<OrderVo> addOrder(@AuthenticationPrincipal Long userId,
                                        @RequestBody OrdersDto ordersDto) {
        OrderVo orderVo = ordersService.addOrder(userId, ordersDto);
        return ResponseVo.success("添加成功", orderVo);
    }

    /**
     * 从购物车下单（闭环主入口）。
     *
     * 请求体只需要：{ "storeId": 1, "fulfillmentType": 1, "remark": "少冰" }
     * 商品明细由服务端从购物车读取，前端传的 items 会被忽略。
     *
     * 成功后购物车被清空，返回的 OrderVo 里 orderStatus = 0（待支付），
     * 前端应立即跳转支付页调用 POST /payment/pay。
     */
    @PostMapping("/addFromCart")
    public ResponseVo<OrderVo> addOrderFromCart(@AuthenticationPrincipal Long userId,
                                                @RequestBody OrdersDto ordersDto) {
        OrderVo orderVo = ordersService.addOrderFromCart(userId, ordersDto);
        return ResponseVo.success("下单成功，请尽快完成支付", orderVo);
    }

    /**
     * 我的订单列表。
     *
     * GET /order/my?orderStatus=1   按状态过滤
     * GET /order/my                 全部
     *
     * orderStatus 允许 0~6，语义见 OrderStatus 注释。
     * 前面接订单卡片时只用这个接口，不要再用「按手机号查」——那是越权入口。
     */
    @GetMapping("/my")
    public ResponseVo<List<OrderVo>> getMyOrders(@AuthenticationPrincipal Long userId,
                                                 @RequestParam(required = false) Integer orderStatus) {
        List<OrderVo> orderVoList = ordersService.getMyOrders(userId, orderStatus);
        return ResponseVo.success("查询成功", orderVoList);
    }

    /**
     * 订单详情（含商品明细）。
     *
     * 用 orderNo 而不是数据库 id 对外暴露：
     * 自增 id 会泄露订单总量，也会诱导别人顺序遍历；orderNo 是业务号，天然可对外。
     *
     * 服务端会校验该订单属于当前登录用户，否则返回 403。
     */
    @GetMapping("/detail")
    public ResponseVo<OrderVo> getOrderDetail(@AuthenticationPrincipal Long userId,
                                              @RequestParam String orderNo) {
        OrderVo orderVo = ordersService.getMyOrderDetail(userId, orderNo);
        return ResponseVo.success("查询成功", orderVo);
    }

    /**
     * 取消订单（仅本人，且仅待支付/待接单状态）。
     *
     * 已支付但门店未接单时取消，服务端会连带发起退款；已开始制作后只能走退款流程。
     */
    @PostMapping("/cancel")
    public ResponseVo<OrderVo> cancelOrder(@AuthenticationPrincipal Long userId,
                                           @RequestParam Long orderId) {
        OrderVo orderVo = ordersService.cancelOrder(userId, orderId);
        return ResponseVo.success("取消成功", orderVo);
    }

    /**
     * 门店接单（PAID → MAKING）。门店端接口，闭环阶段临时放在这里。
     */
    @PostMapping("/accept")
    public ResponseVo<OrderVo> acceptOrder(@RequestParam Long orderId) {
        OrderVo orderVo = ordersService.acceptOrder(orderId);
        return ResponseVo.success("接单成功", orderVo);
    }

    /**
     * 制作完成（MAKING → READY）。门店端接口。
     */
    @PostMapping("/finishMaking")
    public ResponseVo<OrderVo> finishMaking(@RequestParam Long orderId) {
        OrderVo orderVo = ordersService.finishMaking(orderId);
        return ResponseVo.success("制作完成", orderVo);
    }

    /**
     * 取餐核销（→ COMPLETED），闭环终点。门店端接口。
     *
     * 必须同时传 storeId 和 pickupCode：取餐码只在同门店内唯一，
     * 只传码可能核销到别家店的订单。
     */
    @PostMapping("/complete")
    public ResponseVo<OrderVo> completeOrder(@RequestParam Long storeId,
                                             @RequestParam String pickupCode) {
        OrderVo orderVo = ordersService.completeOrder(storeId, pickupCode);
        return ResponseVo.success("核销成功，订单已完成", orderVo);
    }
}
