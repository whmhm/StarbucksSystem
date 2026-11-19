package com.whm.starbucks.services;

import com.whm.starbucks.dao.OrderItemRepository;
import com.whm.starbucks.dao.OrdersRepository;
import com.whm.starbucks.dao.UserRepository;
import com.whm.starbucks.exceptions.BizException;
import com.whm.starbucks.pojo.dto.OrderItemDto;
import com.whm.starbucks.pojo.dto.OrdersDto;
import com.whm.starbucks.pojo.entity.OrderItem;
import com.whm.starbucks.pojo.entity.Orders;
import com.whm.starbucks.pojo.entity.User;
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
        Map<Long, ProductVo> productMap = loadProducts(productIds);

        // ---------- 4. 金额边界：只用服务端价格，前端传价一律忽略 ----------
        List<OrderItem> orderItems = new ArrayList<>(items.size());
        BigDecimal goodsTotal = BigDecimal.ZERO;
        for (OrderItemDto item : items) {
            ProductVo product = productMap.get(item.getProductId());
            BigDecimal unitPrice = scale2(product.getBasePrice());
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
     * 批量查商品并校验：查不到的说明不存在/已下架/已删除（SQL 已过滤 is_available/is_deleted）。
     * TODO 门店-商品可售（store_product）与库存校验待该表接入后补在这里
     */
    private Map<Long, ProductVo> loadProducts(List<Long> productIds) {
        List<ProductVo> products = productService.selectProductsByIds(productIds);
        Map<Long, ProductVo> productMap = new LinkedHashMap<>();
        if (products != null) {
            for (ProductVo product : products) {
                productMap.put(product.getId(), product);
            }
        }
        for (Long productId : productIds) {
            ProductVo product = productMap.get(productId);
            if (product == null) {
                throw new BizException("商品不存在或已下架，商品ID：" + productId);
            }
            if (product.getBasePrice() == null) {
                throw new BizException("商品价格异常：" + product.getProductName());
            }
        }
        return productMap;
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
}
