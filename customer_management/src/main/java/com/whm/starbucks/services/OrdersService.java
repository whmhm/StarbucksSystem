package com.whm.starbucks.services;

import com.whm.starbucks.dao.CartItemRepository;
import com.whm.starbucks.dao.CartRepository;
import com.whm.starbucks.dao.OrderItemRepository;
import com.whm.starbucks.dao.OrdersRepository;
import com.whm.starbucks.dao.UserRepository;
import com.whm.starbucks.exceptions.BizException;
import com.whm.starbucks.pojo.dto.OrderItemDto;
import com.whm.starbucks.pojo.dto.OrdersDto;
import com.whm.starbucks.pojo.entity.Cart;
import com.whm.starbucks.pojo.entity.CartItem;
import com.whm.starbucks.pojo.entity.OrderItem;
import com.whm.starbucks.pojo.entity.Orders;
import com.whm.starbucks.pojo.entity.User;
import com.whm.starbucks.pojo.enums.OrderStatus;
import com.whm.starbucks.pojo.vo.OrderItemVo;
import com.whm.starbucks.pojo.vo.OrderVo;
import com.whm.starbucks.pojo.vo.ProductVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class OrdersService {

    /** 单笔订单最多商品种类数，防止刷单与长事务 */
    private static final int MAX_ITEM_KINDS = 20;
    /** 单个商品最大数量 */
    private static final int MAX_QUANTITY = 99;
    /** 备注最大长度，需与 orders.remark 列宽保持一致 */
    private static final int REMARK_MAX_LENGTH = 200;
    /** 定制快照最大长度，需与 order_items.customization_snapshot 列宽保持一致 */
    private static final int CUSTOMIZATION_SNAPSHOT_MAX_LENGTH = 500;
    /** 单笔订单金额上限 */
    private static final BigDecimal MAX_ORDER_AMOUNT = new BigDecimal("99999.99");
    /** 单件定制加价上限：定制项表未建，先做兜底 */
    private static final BigDecimal MAX_CUSTOMIZATION_PRICE = new BigDecimal("99.99");
    /** 订单状态：待支付 */
    private static final int ORDER_STATUS_UNPAID = 0;
    /** 预计完成时间：下单后固定分钟数（MVP） */
    private static final int READY_MINUTES = 10;

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    @Resource
    private OrdersRepository ordersRepository;

    @Resource
    private OrderItemRepository orderItemRepository;

    @Resource
    private UserRepository usersRepository;

    @Autowired
    private ProductService productService;

    /** 购物车车头：下单要按 (userId, storeId) 把车读出来 */
    @Resource
    private CartRepository cartRepository;

    /** 购物车明细：转成订单明细后要清空 */
    @Resource
    private CartItemRepository cartItemRepository;

    /**
     * 「我的订单」列表（闭环的查询入口）。
     *
     * TODO(你来实现)，步骤：
     *   1. userId 为 null → BizException(401, "未登录或登录已失效")；
     *   2. orderStatus 非空时校验范围 [OrderStatus.UNPAID, OrderStatus.REFUNDED]，
     *      越界 → BizException("订单状态不合法")（不校验的话 SQL 会直接过滤出空结果，前端看不到哪里错了）；
     *   3. ordersRepository.selectOrdersByUserId(userId, orderStatus)；
     *   4. 空 → Collections.emptyList()；
     *   5. 逐条转 OrderVo（可复用 toOrderVo 的字段映射，但 items 传空 List）。
     *
     * 性能约定：列表页【不】查明细。
     *   若要展示「共 3 件商品」，用另外一条 count 聚合查询，
     *   不要在列表里对每条订单调一次 selectOrderItemsByOrderId（典型 N+1）。
     *
     * 为什么不复用下面的 getOrdersByUserPhone：那个方法按手机号查且不校验归属，
     * 任何登录用户拿到别人手机号就能看别人订单，是属于要下线的接口。
     */
    public List<OrderVo> getMyOrders(Long userId, Integer orderStatus) {
        throw new BizException(501, "功能待实现：OrdersService.getMyOrders");
    }

    /**
     * 订单详情（带明细 + 归属校验），订单页/支付完成页都调它。
     *
     * TODO(你来实现)，顺序不能调：
     *   1. userId / orderNo 为空 → BizException；
     *   2. ordersRepository.selectOrderByNo(orderNo)，为 null → BizException(404, "订单不存在");
     *   3. 【归属校验】orders.getUserId() 与 userId 不一致 → BizException(403, "无权查看该订单");
     *      —— 必须在返回前校验，否则只改个 orderNo 就能看别人的收货信息；
     *   4. 补明细：orderItemRepository.selectOrderItemsByOrderId(orders.getId())，
     *      用 toOrderVo(orders, items) 组装，这样 OrderVo.items 不再是 null；
     *   5. 现有 getOrderByNo 没有明细也没有归属校验，此处不复用它。
     *
     * 门店端/运营端将来要查跨用户订单，应另建带角色校验的接口，
     * 不要给这个方法加“管理员绕过”的分支，否则越权防线会在两面都可以击破。
     */
    public OrderVo getMyOrderDetail(Long userId, String orderNo) {
        throw new BizException(501, "功能待实现：OrdersService.getMyOrderDetail");
    }

    /**
     * @deprecated 按手机号查订单且不校验归属，属于越权接口。
     *             请改用 {@link #getMyOrders(Long, Integer)}，本方法保留只是为了不破坏既有调用。
     */
    @Deprecated
    public List<OrderVo> getOrdersByUserPhone(String phone) {
        List<Orders> ordersList = ordersRepository.selectOrderByUserPhone(phone);
        // orders 是 数组 判断订单是否存在
        if (ordersList == null || ordersList.isEmpty()) {
            return Collections.emptyList();
        }
//        orders 转 ordersVo
        return ordersList.stream().map(orders -> {
                    OrderVo ordersVo = new OrderVo();
                    ordersVo.setOrderNo(orders.getOrderNo());
                    ordersVo.setUserPhone(orders.getUserPhone());
                    ordersVo.setStoreId(orders.getStoreId());
                    ordersVo.setFulfillmentType(orders.getFulfillmentType());
                    ordersVo.setOrderStatus(orders.getOrderStatus());
                    ordersVo.setTotalAmount(orders.getTotalAmount());
                    ordersVo.setDiscountAmount(orders.getDiscountAmount());
                    ordersVo.setPayAmount(orders.getPayAmount());
                    ordersVo.setPickupCode(orders.getPickupCode());
                    ordersVo.setEstimatedReadyTime(orders.getEstimatedReadyTime());
                    ordersVo.setRemark(orders.getRemark());
                    return ordersVo;
                }).collect(Collectors.toList());
    }


    /**
     * @deprecated 按业务号查订单但不校验归属，且不返回明细，属于越权入口。
     *             请改用 {@link #getMyOrderDetail(Long, String)}。
     *             Controller 已不再暴露此方法，保留仅为兼容。
     */
    @Deprecated
    public OrderVo getOrderByNo(String orderNo) {
        Orders orders = ordersRepository.selectOrderByNo(orderNo);
        if (orders == null) {
            return null;
        }
        OrderVo ordersVo = new OrderVo();

        ordersVo.setOrderNo(orders.getOrderNo());
        ordersVo.setUserPhone(orders.getUserPhone());
        ordersVo.setStoreId(orders.getStoreId());
        ordersVo.setFulfillmentType(orders.getFulfillmentType());
        ordersVo.setOrderStatus(orders.getOrderStatus());
        ordersVo.setTotalAmount(orders.getTotalAmount());
        ordersVo.setDiscountAmount(orders.getDiscountAmount());
        ordersVo.setPayAmount(orders.getPayAmount());
        ordersVo.setPickupCode(orders.getPickupCode());
        ordersVo.setEstimatedReadyTime(orders.getEstimatedReadyTime());
        ordersVo.setRemark(orders.getRemark());
        return ordersVo;
    }

    /**
     * 下单主流程：
     * 1. 参数边界校验（快速失败，不碰 DB）
     * 2. 用户校验（userId 由 JWT 传入，必须存在且未注销）
     * 3. 一次性批量查商品并校验可售、价格
     * 4. 用服务端价格计算明细金额与订单总额
     * 5. 生成 orderNo / pickupCode / estimatedReadyTime
     * 6. 插主表（回填自增 id），影响行数 != 1 直接抛异常
     * 7. 主表成功后再插子表，每条明细绑定 orderId，行数不符抛异常
     * 8. 用落库结果组装 OrderVo（含明细）返回
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVo addOrder(Long userId, OrdersDto ordersDto) {
        // ---------- 1. 入参边界校验 ----------
        if (userId == null) {
            throw new BizException(401, "未登录或登录已失效");
        }
        if (ordersDto == null) {
            throw new BizException("订单信息不能为空");
        }
        List<OrderItemDto> items = ordersDto.getItems();
        validateItems(items);
        validateStoreAndFulfillment(ordersDto);
        String remark = normalizeRemark(ordersDto.getRemark());

        // ---------- 2. 用户边界 ----------
        User user = usersRepository.getUserById(userId);
        if (user == null) {
            throw new BizException(401, "用户不存在或已注销");
        }
        if (!StringUtils.hasText(user.getPhone())) {
            throw new BizException("请先绑定手机号后再下单");
        }

        // ---------- 3. 商品边界：去重后一次批量查询，避免 N+1 ----------
        List<Long> productIds = items.stream()
                .map(OrderItemDto::getProductId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, ProductVo> productMap = loadStoreProducts(ordersDto.getStoreId(), productIds);

        // ---------- 4. 金额边界：只用服务端价格，前端传价一律忽略 ----------
        List<OrderItem> orderItems = new ArrayList<>(items.size());
        BigDecimal goodsTotal = BigDecimal.ZERO;
        for (OrderItemDto item : items) {
            ProductVo product = productMap.get(item.getProductId());
            BigDecimal unitPrice = resolveUnitPrice(product);
            BigDecimal customizationPrice = checkCustomizationPrice(item.getCustomizationPrice());
            BigDecimal itemTotalPrice = unitPrice.add(customizationPrice)
                    .multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            OrderItem orderItem = new OrderItem();
            orderItem.setProductId(product.getId());
            orderItem.setProductName(product.getProductName());   // 商品名快照
            orderItem.setQuantity(item.getQuantity());
            orderItem.setUnitPrice(unitPrice);                    // 服务端单价快照
            orderItem.setCustomizationPrice(customizationPrice);
            orderItem.setCustomizationSnapshot(item.getCustomizationSnapshot());
            orderItem.setItemTotalPrice(itemTotalPrice);
            orderItems.add(orderItem);

            goodsTotal = goodsTotal.add(itemTotalPrice);
        }
        goodsTotal = scale2(goodsTotal);
        if (goodsTotal.compareTo(MAX_ORDER_AMOUNT) > 0) {
            throw new BizException("订单金额超出上限");
        }

        // MVP 不做优惠，折扣恒为 0；接入优惠券后在这里扣减并重新校验非负
        BigDecimal discountAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal payAmount = goodsTotal.subtract(discountAmount);
        if (payAmount.signum() < 0) {
            throw new BizException("实付金额不能为负数");
        }

        // ---------- 5. 单号 / 取餐码 / 预计完成时间 ----------
        LocalDateTime orderTime = LocalDateTime.now();
        String now = orderTime.format(DATE_TIME);
        String estimatedReadyTime = orderTime.plusMinutes(READY_MINUTES).format(DATE_TIME);

        Orders order = new Orders();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setUserPhone(user.getPhone());               // 手机号快照
        order.setStoreId(ordersDto.getStoreId());
        order.setFulfillmentType(ordersDto.getFulfillmentType());
        order.setOrderStatus(ORDER_STATUS_UNPAID);
        order.setTotalAmount(goodsTotal);
        order.setDiscountAmount(discountAmount);
        order.setPayAmount(payAmount);
        order.setPickupCode(generatePickupCode());
        order.setEstimatedReadyTime(estimatedReadyTime);
        order.setRemark(remark);
        order.setIsDeleted(0);
        order.setGmtCreate(now);
        order.setGmtModified(now);

        // ---------- 6. 先插主表，依赖自增主键回填得到 orderId ----------
        int orderRows = ordersRepository.insertOrder(order);
        if (orderRows != 1) {
            throw new BizException(500, "订单创建失败");
        }
        if (order.getId() == null) {
            throw new BizException(500, "订单主键回填失败，请检查 insertOrder 的 useGeneratedKeys 配置");
        }

        // ---------- 7. 主表成功后再插子表 ----------
        for (OrderItem orderItem : orderItems) {
            orderItem.setOrderId(order.getId());
        }
        int itemRows = orderItemRepository.insertOrderItems(orderItems);
        if (itemRows != orderItems.size()) {
            throw new BizException(500, "订单明细创建失败");
        }

        // ---------- 8. 用落库结果组装返回值 ----------
        return toOrderVo(order, orderItems);
    }

    /**
     * 从购物车下单 —— 闭环主入口，替代前端自己拼 items 的 addOrder。
     *
     * 为什么要有这个方法：
     *   现在的 addOrder 要求前端把商品列表再传一遍，等于前端把购物车内容解密后又发给后端，
     *   两边一旦不一致（用户改了规格、商品下架、价格变了）就会产生脏订单；
     *   而且下单成功后购物车还留着，用户回到购物车会发现商品还在，体验上就是“没下单成功”。
     *
     * TODO(你来实现)，顺序不能调：
     *   1. 入参：userId null → BizException(401, "未登录或登录已失效")；
     *      ordersDto null → BizException("订单信息不能为空")；
     *      复用 validateStoreAndFulfillment(ordersDto) 校验 storeId 与 fulfillmentType。
     *   2. 取车头：cartRepository.getCart(userId, storeId)；
     *      为 null → BizException("购物车为空，请先选购商品")。
     *   3. 取明细：cartItemRepository.getCartItems(cart.getId())；
     *      空 → 同上（不能建一张 0 元订单）。
     *   4. 转 DTO：每条 CartItem → new OrderItemDto()，
     *      只回填 productId / quantity / customizationPrice / customizationSnapshot；
     *      【不要】把 cartItem 里的 unitPrice / itemTotalPrice 抄过去：
     *        addOrder 会用门店当前价重算，购物车里的价可能已经过期（调价/下架）。
     *      客制化加价暂时仍需透传（定制项表未建，见 checkCustomizationPrice 的 TODO）。
     *   5. 组装新的 OrdersDto：storeId、fulfillmentType、remark 原样透传，setItems(转换结果)。
     *   6. 调 addOrder(userId, checkoutDto)；
     *      【自调用陷阱】同一个类里直接调 @Transactional 方法不会走代理，
     *      但本方法自己也标了 @Transactional，所以两步仍然在同一个事务里，这是可以接受的前提。
     *      如果以后把本方法的事务去掉，这里就会变成两次独立提交，必须改用注入自身代理或抽成两个类。
     *   7. 下单成功后清车：cartItemRepository.deleteByCartId(cart.getId())。
     *      【顺序】必须先下单成功再清车。反过来一旦建单失败，用户的购物车就没了。
     *      返回行数不必强等于明细数（可能被并发清空），> 0 或 == 0 都不报错，必要时记 warn 日志。
     *   8. 返回 addOrder 的 OrderVo。
     *
     * 库存扣减：本循环暂不扣 store_product.stock_quantity（只在购物车阶段做了充足性校验）。
     * 若要扣，必须在步骤 6 之后、仍在本事务内，且用带条件 UPDATE（stock >= qty）防超卖。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVo addOrderFromCart(Long userId, OrdersDto ordersDto) {
        throw new BizException(501, "功能待实现：OrdersService.addOrderFromCart");
    }

    private void validateItems(List<OrderItemDto> items) {
        if (items == null || items.isEmpty()) {
            throw new BizException("订单商品不能为空");
        }
        if (items.size() > MAX_ITEM_KINDS) {
            throw new BizException("单笔订单商品种类不能超过" + MAX_ITEM_KINDS + "个");
        }
        for (OrderItemDto item : items) {
            if (item == null || item.getProductId() == null || item.getProductId() <= 0) {
                throw new BizException("商品ID不合法");
            }
            if (item.getQuantity() == null || item.getQuantity() < 1) {
                throw new BizException("商品数量必须大于0");
            }
            if (item.getQuantity() > MAX_QUANTITY) {
                throw new BizException("单个商品数量不能超过" + MAX_QUANTITY + "件");
            }
            String snapshot = item.getCustomizationSnapshot();
            if (snapshot != null && snapshot.length() > CUSTOMIZATION_SNAPSHOT_MAX_LENGTH) {
                throw new BizException("定制信息过长");
            }
        }
    }

    private void validateStoreAndFulfillment(OrdersDto ordersDto) {
        if (ordersDto.getStoreId() == null || ordersDto.getStoreId() <= 0) {
            throw new BizException("请选择下单门店");
        }
        Integer fulfillmentType = ordersDto.getFulfillmentType();
        if (fulfillmentType == null || fulfillmentType < 1 || fulfillmentType > 3) {
            throw new BizException("履约方式不合法");
        }
    }

    private String normalizeRemark(String remark) {
        if (remark == null) {
            return "";
        }
        String trimmed = remark.trim();
        if (trimmed.length() > REMARK_MAX_LENGTH) {
            throw new BizException("备注不能超过" + REMARK_MAX_LENGTH + "个字");
        }
        return trimmed;
    }

    /**
     * 批量查【当前门店】的可售商品并校验。
     *
     * 这里是闭环前必须修的口径 bug：原实现用 productService.selectProductsByIds()，
     * 拿的是商品基础价 base_price，而 CartService 结算用的是门店价 store_price，
     * 两处价不一致会让「购物车显示 38，实付扣了 42」。
     * 现在统一走 getProductsByStore（INNER JOIN store_product），计价口径与购物车一致。
     *
     * 校验点：
     *   - 不在返回结果里的商品 = 该门店不可售/已下架/已删除 → 直接报错；
     *   - 门店价和基础价都为 null → 报价格异常；
     *   - 库存充足性到创建订单前再校验（见 TODO）。
     */
    private Map<Long, ProductVo> loadStoreProducts(Long storeId, List<Long> productIds) {
        List<ProductVo> products = productService.getProductsByStore(storeId);
        Map<Long, ProductVo> productMap = new LinkedHashMap<>();
        if (products != null) {
            for (ProductVo product : products) {
                productMap.put(product.getId(), product);
            }
        }
        for (Long productId : productIds) {
            ProductVo product = productMap.get(productId);
            if (product == null) {
                throw new BizException("商品在所选门店不可售，商品ID：" + productId);
            }
            if (product.getBasePrice() == null && product.getStorePrice() == null) {
                throw new BizException("商品价格异常：" + product.getProductName());
            }
        }
        return productMap;
    }

    /**
     * 统一定价：门店价优先，缺失时回退基础价。
     * 【必须】与 CartService.resolveUnitPrice 保持完全一致，
     * 否则购物车与下单会出现两种价，用户看到的价格和扣款金额对不上。
     */
    private BigDecimal resolveUnitPrice(ProductVo product) {
        BigDecimal price = product.getStorePrice() != null ? product.getStorePrice() : product.getBasePrice();
        if (price == null) {
            throw new BizException("商品价格异常：" + product.getProductName());
        }
        return scale2(price);
    }

    private BigDecimal checkCustomizationPrice(BigDecimal customizationPrice) {
        // TODO 定制项表建好后，必须改为按 customizationSnapshot 服务端计价
        if (customizationPrice == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (customizationPrice.signum() < 0) {
            throw new BizException("定制加价不能为负数");
        }
        if (customizationPrice.compareTo(MAX_CUSTOMIZATION_PRICE) > 0) {
            throw new BizException("定制加价超出上限");
        }
        return scale2(customizationPrice);
    }

    private BigDecimal scale2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private OrderVo toOrderVo(Orders order, List<OrderItem> orderItems) {
        OrderVo orderVo = new OrderVo();
        orderVo.setId(order.getId());
        orderVo.setOrderNo(order.getOrderNo());
        orderVo.setUserPhone(order.getUserPhone());
        orderVo.setStoreId(order.getStoreId());
        orderVo.setFulfillmentType(order.getFulfillmentType());
        orderVo.setOrderStatus(order.getOrderStatus());
        orderVo.setTotalAmount(order.getTotalAmount());
        orderVo.setDiscountAmount(order.getDiscountAmount());
        orderVo.setPayAmount(order.getPayAmount());
        orderVo.setPickupCode(order.getPickupCode());
        orderVo.setEstimatedReadyTime(order.getEstimatedReadyTime());
        orderVo.setRemark(order.getRemark());
        orderVo.setIsDeleted(order.getIsDeleted());
        orderVo.setGmtCreate(order.getGmtCreate());
        orderVo.setGmtModified(order.getGmtModified());

        List<OrderItemVo> itemVos = new ArrayList<>(orderItems.size());
        for (OrderItem orderItem : orderItems) {
            OrderItemVo itemVo = new OrderItemVo();
            itemVo.setOrderId(orderItem.getOrderId());
            itemVo.setProductId(orderItem.getProductId());
            itemVo.setProductName(orderItem.getProductName());
            itemVo.setQuantity(orderItem.getQuantity());
            itemVo.setUnitPrice(orderItem.getUnitPrice());
            itemVo.setCustomizationPrice(orderItem.getCustomizationPrice());
            itemVo.setCustomizationSnapshot(orderItem.getCustomizationSnapshot());
            itemVo.setItemTotalPrice(orderItem.getItemTotalPrice());
            itemVos.add(itemVo);
        }
        orderVo.setItems(itemVos);
        return orderVo;
    }

    private String generateOrderNo() {
        return "NO" + LocalDateTime.now().format(ORDER_NO_TIME)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }

    private String generatePickupCode() {
        // 取餐码：6 位数字，需配合「同门店同营业日唯一」的唯一索引使用
        return String.format("%06d", ThreadLocalRandom.current().nextInt(1000000));
    }

    // ==================================================================
    // 订单状态流转（闭环的「履约」段）
    //
    // 四个动作的共同骨架：取订单 → 校验状态合法 → CAS 改状态 → 回查返回，
    // 差异只在「谁能调」和「从哪个状态出发」，所以公共逻辑抽到 transit()。
    // 直接写 update 语句而不走状态机，是状态倒退的根源：接单和取消并发时，
    // 「已取消」会被覆盖成「制作中」，库存、退款、对账全部对不上。
    // ==================================================================

    /**
     * 用户取消订单。
     *
     * 允许：UNPAID → CANCELLED（还没付款）、PAID → CANCELLED（已付款但门店还没接单）。
     * 不允许：MAKING / READY 之后——咖啡已经在做了，那属于退款流程。
     *
     * TODO(你来实现)：
     *   1. requireOrder(orderId) 取订单；
     *   2. 归属校验：order.getUserId() 与 userId 不一致 → BizException(403, "无权操作该订单")；
     *   3. 若当前状态是 PAID，必须先把支付流水退掉（调 PaymentService.refund），
     *      否则会出现「订单已取消但钱没退」；跨 Service 调用会加入同一事务，
     *      退款抛异常时整个取消一起回滚，这正是想要的。
     *   4. return transit(order, OrderStatus.CANCELLED)；
     *   5. UNPAID 的取消不需要动流水，因为没有支付记录；若已有待支付流水（pay_status=0），
     *      可选地把它置为失败（本循环可先不做，但要在对账口径里说清楚）。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVo cancelOrder(Long userId, Long orderId) {
        throw new BizException(501, "功能待实现：OrdersService.cancelOrder");
    }

    /**
     * 门店接单：PAID → MAKING。
     *
     * TODO(你来实现)：requireOrder(orderId) 后直接 return transit(order, OrderStatus.MAKING)。
     *
     * 【权限缺口，必须知道】当前所有登录用户都只有 ROLE_USER，没有门店/店长角色，
     * 这个接口理论上任何顾客都能调（把自己的订单点成「制作中」）。
     * 闭环阶段先接受这个缺口，但要靠 SecurityConfig 的角色限制或迁到 store_management 模块来堵。
     * 不要在 Controller 里靠注释「约定」权限——那没有任何约束力。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVo acceptOrder(Long orderId) {
        throw new BizException(501, "功能待实现：OrdersService.acceptOrder");
    }

    /**
     * 制作完成：MAKING → READY（此时推送「您的咖啡好了，取餐码 xxxx」）。
     *
     * TODO(你来实现)：requireOrder(orderId) 后 return transit(order, OrderStatus.READY)。
     * 未来接入消息推送时，推送放在事务提交后（TransactionSynchronization afterCommit），
     * 否则事务回滚了消息已经发出去了。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVo finishMaking(Long orderId) {
        throw new BizException(501, "功能待实现：OrdersService.finishMaking");
    }

    /**
     * 取餐核销：门店侧输码，PAID/MAKING/READY → COMPLETED，订单闭环终点。
     *
     * TODO(你来实现)：
     *   1. storeId / pickupCode 为空或空字符串 → BizException("请提供门店与取餐码")；
     *   2. ordersRepository.selectByStoreAndPickupCode(storeId, pickupCode) 查不到
     *      → BizException(404, "未找到可核销的订单")；
     *   3. return transit(order, OrderStatus.COMPLETED)。
     *
     * 为什么不按 orderId 核销：店员手里只有取餐码，没有订单 id；
     * 为什么必须带 storeId：取餐码只在同门店内唯一，只靠 6 位数字会核销到别家店。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVo completeOrder(Long storeId, String pickupCode) {
        throw new BizException(501, "功能待实现：OrdersService.completeOrder");
    }

    /**
     * 状态流转公共骨架（上面四个方法都调它）。
     *
     * TODO(你来实现)：
     *   1. OrderStatus.canTransit(order.getOrderStatus(), targetStatus) 为 false
     *      → BizException(409, "订单状态不支持该操作：" + OrderStatus.descOf(当前)
     *                         + " → " + OrderStatus.descOf(targetStatus))；
     *   2. ordersRepository.updateOrderStatus(order.getId(), order.getOrderStatus(), targetStatus)；
     *      rows == 0 → BizException(409, "订单状态已变更，请刷新后重试")
     *      （0 行说明并发下状态已被其他请求改掉，这是必须暴露的冲突，不能“再查一次然后继续”）；
     *   3. 回查 selectOrderById 拿最新 gmtModified，
     *      需要明细的调用方（详情页）自行加载，不要在这里固定多查一次明细。
     */
    private OrderVo transit(Orders order, int targetStatus) {
        throw new BizException(501, "功能待实现：OrdersService.transit");
    }

    /**
     * 按主键取订单，不存在直接 404。
     * TODO(你来实现)：orderId 为空 → BizException("订单ID不能为空")；
     *                selectOrderById 为 null → BizException(404, "订单不存在")。
     */
    private Orders requireOrder(Long orderId) {
        throw new BizException(501, "功能待实现：OrdersService.requireOrder");
    }
}
