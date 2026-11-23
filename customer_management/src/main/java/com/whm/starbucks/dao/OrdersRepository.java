package com.whm.starbucks.dao;

import com.whm.starbucks.pojo.entity.Orders;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;


import java.util.List;

@Repository
public interface OrdersRepository {
    List<Orders> selectOrderByUserPhone(@Param("phone") String phone);
    Orders selectOrderByNo(@Param("orderNo") String orderNo);
    int insertOrder(@Param("orders") Orders orders);
    int updateOrder(@Param("orders") Orders orders);

    // ==================================================================
    // 以下为「订单闭环」新增，Service 侧方法体待你实现
    // ==================================================================

    /**
     * 按用户查订单（「我的订单」）。
     *
     * 这是替换 selectOrderByUserPhone 的正确做法：
     * userId 来自 JWT，用户改不了；phone 是明文可猜的外部标识，不能当权限凭证。
     *
     * @param userId 当前登录用户主键
     * @param status 订单状态过滤，传 null 表示全部
     */
    List<Orders> selectOrdersByUserId(@Param("userId") Long userId,
                                      @Param("orderStatus") Integer orderStatus);

    /** 按主键查订单，支付/状态流转都需要先按 id 拿到订单再校验归属 */
    Orders selectOrderById(@Param("id") Long id);

    /**
     * 带状态守卫的状态流转：只有当库里的状态等于 expectedStatus 时才改成 targetStatus。
     *
     * 为什么必须带 expectedStatus：
     * 若不带，两个并发请求（例如重复点「取消」）都能改成目标状态，
     * 会把「已完成」的订单覆盖成「已取消」，导致状态倒退。
     *
     * @return 受影响行数，1 = 本次真正发生了状态跃迁，0 = 状态已变/订单不存在
     */
    int updateOrderStatus(@Param("id") Long id,
                          @Param("expectedStatus") Integer expectedStatus,
                          @Param("targetStatus") Integer targetStatus);

    /**
     * 按「门店 + 取餐码」查当前仍在履约中的订单，用于取餐核销。
     *
     * 为什么不能只按 pickupCode 查：
     * 取餐码只有 6 位且只在同门店内唯一，不同门店撞码是常态；
     * 只按码查会核销到别的门店的订单。
     * 只查 1/2/3（待接单/制作中/待取餐）是必须的：
     * 已完成的订单再次输码不应重复核销。
     */
    Orders selectByStoreAndPickupCode(@Param("storeId") Long storeId,
                                      @Param("pickupCode") String pickupCode);
}
