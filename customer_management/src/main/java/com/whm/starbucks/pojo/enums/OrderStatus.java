package com.whm.starbucks.pojo.enums;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 订单状态常量 + 状态机定义。
 *
 * 为什么单独抽一个类：
 * 现在 OrdersService 里只有一个 ORDER_STATUS_UNPAID 常量，其余 1~6 全靠魔法数字散落在各处，
 * 状态流转一旦多起来，「这个 3 到底是待取餐还是已退款」就没人说得准了。
 *
 * ============================ 状态图 ============================
 *
 *   0 待支付 ──支付成功──> 1 已支付/待接单 ──接单──> 2 制作中 ──制作完成──> 3 待取餐 ──核销──> 4 已完成
 *     │                      │                    │                  │
 *     │ 用户取消/超时关单      │ 用户取消            │ 退款              │ 退款
 *     └──────────┐           └──────────┐         └────────┐         └────────┐
 *                ▼                      ▼                  ▼                  ▼
 *              5 已取消                5 已取消         6 已退款           6 已退款
 *
 * 终态：4 已完成、5 已取消、6 已退款 —— 进入终态后不再接受任何流转。
 * 注意「已完成」之后要走售后/退款，那属于另一个流程（可引入 7 售后中），本循环不做。
 * =================================================================
 *
 * 与数据库对齐：orders.order_status 的列注释就是这套 0~6，改动必须同时改表注释。
 */
public final class OrderStatus {

    /** 待支付：下单成功但未付款，可取消，超时（建议 15 分钟）自动关单 */
    public static final int UNPAID = 0;
    /** 已支付/待接单：支付流水落库成功，等待门店接单 */
    public static final int PAID = 1;
    /** 制作中：门店已接单，开始制作 */
    public static final int MAKING = 2;
    /** 待取餐/配送中：制作完成，等待顾客凭取餐码核销 */
    public static final int READY = 3;
    /** 已完成：取餐码核销成功，闭环终点 */
    public static final int COMPLETED = 4;
    /** 已取消：用户取消或超时未支付，闭环终点 */
    public static final int CANCELLED = 5;
    /** 已退款：支付成功后又退款，闭环终点 */
    public static final int REFUNDED = 6;

    /**
     * 状态迁移表：key = 当前状态，value = 允许迁往的状态集合。
     *
     * 特意定义成 static final 而不是方法内局部变量：
     * canTransit 在每次状态流转时都会被调，原实现每次调用都新建 1 个 HashMap + 7 个 Set，
     * 纯属无效开销，而且迁移规则散在方法体里也不好审阅。
     */
    private static final Map<Integer, Set<Integer>> TRANSIT_MAP;

    static {
        Map<Integer, Set<Integer>> map = new HashMap<>(16);
        // 【注意】UNPAID -> PAID 必须在这张表里。
        // 支付虽然由 PaymentService 驱动，但同样要过状态机校验，
        // 漏了它 canTransit(0, 1) 恒为 false，整个支付流程永远走不通。
        map.put(UNPAID, new HashSet<>(Arrays.asList(PAID, CANCELLED)));
        map.put(PAID, new HashSet<>(Arrays.asList(MAKING, CANCELLED, REFUNDED)));
        map.put(MAKING, new HashSet<>(Arrays.asList(READY, REFUNDED)));
        map.put(READY, new HashSet<>(Arrays.asList(COMPLETED, REFUNDED)));
        // 终态：用 emptySet 占位而不是不给 key，这样 get 不会返回 null，调用方少一层判空
        map.put(COMPLETED, Collections.emptySet());
        map.put(CANCELLED, Collections.emptySet());
        map.put(REFUNDED, Collections.emptySet());
        TRANSIT_MAP = Collections.unmodifiableMap(map);
    }

    private OrderStatus() {
        // 常量类不允许实例化
    }

    /**
     * 状态文案，给前端/日志用。
     * default 保留「未知状态(n)」，避免库里的脏数据让前端拿到 null。
     */
    public static String descOf(int status) {
        switch (status) {
            case UNPAID:
                return "待支付";
            case PAID:
                return "已支付/待接单";
            case MAKING:
                return "制作中";
            case READY:
                return "待取餐/配送中";
            case COMPLETED:
                return "已完成";
            case CANCELLED:
                return "已取消";
            case REFUNDED:
                return "已退款";
            default:
                return "未知状态(" + status + ")";
        }
    }

    /**
     * 判断 from → to 是否允许。
     *
     * 规则全在 TRANSIT_MAP 里，本方法只负责查表：
     *   UNPAID    -> {PAID, CANCELLED}
     *   PAID      -> {MAKING, CANCELLED, REFUNDED}
     *   MAKING    -> {READY, REFUNDED}
     *   READY     -> {COMPLETED, REFUNDED}
     *   COMPLETED / CANCELLED / REFUNDED -> {}   （终态）
     *
     * from == to 一律 false：没有任何业务需要「自己迁到自己」，
     * 放行会让重复点击静默成功，掩盖并发问题。
     */
    public static boolean canTransit(int from, int to) {
        if (from == to) {
            return false;
        }
        Set<Integer> allowed = TRANSIT_MAP.get(from);
        // 未知状态（例如库里的脏数据 99）一律拒绝，不默认放行
        return allowed != null && allowed.contains(to);
    }

    /** 是否终态（用于「已完成/已取消/已退款」不允许再操作的前置判断） */
    public static boolean isFinal(int status) {
        return status == COMPLETED || status == CANCELLED || status == REFUNDED;
    }
}
