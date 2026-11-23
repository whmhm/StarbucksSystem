package com.whm.starbucks.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.whm.starbucks.dao.CartItemRepository;
import com.whm.starbucks.dao.CartRepository;
import com.whm.starbucks.dao.ProductRepository;
import com.whm.starbucks.exceptions.BizException;
import com.whm.starbucks.pojo.dto.CartDto;
import com.whm.starbucks.pojo.dto.CartItemDto;
import com.whm.starbucks.pojo.dto.CartItemUpdateDto;
import com.whm.starbucks.pojo.entity.Cart;
import com.whm.starbucks.pojo.entity.CartItem;
import com.whm.starbucks.pojo.entity.Product;
import com.whm.starbucks.pojo.vo.CartItemVo;
import com.whm.starbucks.pojo.vo.CartVo;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 购物车服务。
 * 数据模型：cart 车头（user_id + store_id 唯一）+ cart_item 明细（cart_id 关联）。
 * 下单接口不再接收前端拼的 items，而是从购物车读取；
 * 推荐流程：用户点“去结算” → GET /cart/list 展示确认页 → POST /order/add 由后端按购物车行生成订单 → 下单成功后清空购物车。
 */
@Service
public class CartService {

    /** 单个商品最大数量，与下单侧保持一致 */
    private static final int MAX_QUANTITY = 99;
    /** 购物车内商品种类上限，与下单侧保持一致 */
    private static final int MAX_ITEM_KINDS = 20;
    /** 定制快照最大长度，需与 cart_item.customization_snapshot 列宽保持一致 */
    private static final int CUSTOMIZATION_SNAPSHOT_MAX_LENGTH = 500;
    /** 单件定制加价上限：定制项表未建，先做兜底 */
    private static final BigDecimal MAX_CUSTOMIZATION_PRICE = new BigDecimal("99.99");

    @Resource
    private CartRepository cartRepository;
    @Resource
    private CartItemRepository cartItemRepository;
    @Resource
    private ProductRepository productRepository;
    @Resource
    private ObjectMapper objectMapper;

    /**
     * 获取购物车列表。
     * 车头不存在时创建空车，保证前端拿到的是空列表而不是 null。
     */
    public CartVo getCart(Long userId, Long storeId) {
        checkUserAndStore(userId, storeId);
        return buildCartVo(getOrCreateCart(userId, storeId));
    }

    /**
     * 添加购物车（整条链路的主入口）：
     * 1. 入参边界校验（快速失败，不碰 DB）
     * 2. 确保 cart 头存在（user_id + store_id），不存在则插入并回填自增 id
     * 3. 一次性查该门店可售商品，拿到门店价与库存，避免 N+1
     * 4. 先把本次请求内“同商品 + 同客制化快照”的重复项合并数量，
     *    再与购物车已有行比对：已存在则累加数量并重算金额，不存在则收集待批量插入
     * 5. 落库后按 cartId 回查整车，返回完整购物车快照
     */
    @Transactional(rollbackFor = Exception.class)
    public CartVo addCartItem(Long userId, CartDto cartDto) {
        // ---------- 1. 入参边界校验 ----------
        if (cartDto == null) {
            throw new BizException("购物车信息不能为空");
        }
        checkUserAndStore(userId, cartDto.getStoreId());
        List<CartItemDto> itemDtos = cartDto.getCartItemList();
        validateItems(itemDtos);

        // ---------- 2. 车头：不存在则创建 ----------
        Cart cart = getOrCreateCart(userId, cartDto.getStoreId());

        // ---------- 3. 门店可售商品（含门店价、库存） ----------
        Map<Long, Product> productMap = loadStoreProducts(cartDto.getStoreId());

        // ---------- 4. 请求内先去重累加，避免同一行被拆成多条 ----------
        Map<String, Integer> quantityByKey = new LinkedHashMap<>();
        Map<String, CartItemDto> dtoByKey = new LinkedHashMap<>();
        for (CartItemDto itemDto : itemDtos) {
            String key = mergeKey(itemDto.getProductId(), itemDto.getCustomizationSnapshot());
            quantityByKey.merge(key, itemDto.getQuantity(), Integer::sum);
            dtoByKey.putIfAbsent(key, itemDto);
        }
        if (quantityByKey.size() > MAX_ITEM_KINDS) {
            throw new BizException("购物车商品种类不能超过" + MAX_ITEM_KINDS + "个");
        }

        // 已有行按 productId + 客制化快照建索引，用于合并数量
        Map<String, CartItem> existingItems = getCartItems(cart.getId()).stream()
                .collect(Collectors.toMap(
                        item -> mergeKey(item.getProductId(), item.getCustomizationSnapshot()),
                        item -> item,
                        (left, right) -> left));

        List<CartItem> toInsert = new ArrayList<>(quantityByKey.size());
        for (Map.Entry<String, Integer> entry : quantityByKey.entrySet()) {
            String key = entry.getKey();
            int quantity = entry.getValue();
            CartItemDto itemDto = dtoByKey.get(key);

            Product product = productMap.get(itemDto.getProductId());
            if (product == null) {
                throw new BizException("商品在所选门店不可售，商品ID：" + itemDto.getProductId());
            }
            // 单价、加价一律用服务端数据，前端传价不参与计算
            BigDecimal unitPrice = resolveUnitPrice(product);
            BigDecimal customizationPrice = checkCustomizationPrice(itemDto.getCustomizationPrice());

            CartItem exist = existingItems.get(key);
            if (exist != null) {
                int newQuantity = exist.getQuantity() + quantity;
                checkQuantityAndStock(product, newQuantity);
                CartItem update = new CartItem();
                update.setId(exist.getId());
                update.setQuantity(newQuantity);
                update.setUnitPrice(unitPrice);
                update.setCustomizationPrice(customizationPrice);
                update.setItemTotalPrice(itemTotalPrice(unitPrice, customizationPrice, newQuantity));
                int rows = cartItemRepository.updateCartItem(update);
                if (rows != 1) {
                    throw new BizException("购物车商品已变更，请刷新后重试");
                }
            } else {
                checkQuantityAndStock(product, quantity);
                toInsert.add(buildCartItem(cart.getId(), product, itemDto, unitPrice, customizationPrice, quantity));
            }
        }
        if (!toInsert.isEmpty()) {
            int rows = cartItemRepository.addCartItems(toInsert);
            if (rows != toInsert.size()) {
                throw new BizException(500, "购物车保存失败");
            }
        }

        // ---------- 5. 回查整车作为返回值 ----------
        return buildCartVo(cart);
    }

    /**
     * 修改购物车单行数量。
     *
     * 签名变更说明：原来传的是 (userId, storeId, productId, quantity)，
     * 但「同商品 + 不同客制化」在购物车里是多行，用 productId 定位会改错行，
     * 所以改成用明细主键 cartItemId 定位（前端在 /car/list 的返回里已经能拿到 id）。
     *
     * TODO(你来实现)，步骤：
     *   1. 入参边界：userId 为 null → BizException(401, "未登录或登录已失效")；
     *      dto / cartItemId 为空 → BizException("购物车条目不能为空")；
     *      quantity == null 或 < 1 → BizException("商品数量必须大于0")；
     *      quantity > MAX_QUANTITY → BizException("单个商品数量不能超过" + MAX_QUANTITY + "件")。
     *   2. 取车头：cartRepository.getCart(userId, storeId)；
     *      为 null → BizException(404, "购物车不存在")。
     *      【注意】这里故意不用 getOrCreateCart：改数量是「改已有数据」，
     *      顺手给用户建一个空车是脏写，会让「null 车」和「空车」两种语义混在一起。
     *   3. 取行并校验归属：cartItemRepository.selectByIdAndCartId(cartItemId, cart.getId())；
     *      为 null → BizException(403, "购物车中没有该商品")
     *      （不区分「不存在」和「不属于你」，避免泄露别人购物车的明细主键是否存在）。
     *   4. 重新取价（关键）：用 loadStoreProducts(storeId) 拿到当前门店价与库存，
     *      不能用行里原有的 unitPrice —— 购物车可能是几天前加的，商品可能已调价或下架。
     *      商品不在 map 里 → BizException("商品在所选门店不可售"）；
     *      库存校验走 checkQuantityAndStock(product, quantity)。
     *   5. 重算行金额：itemTotalPrice(unitPrice, customizationPrice, quantity)，
     *      customizationPrice 沿用行里已存的（客制化选择没变，不重新计价）。
     *   6. 写库：cartItemRepository.updateCartItemQuantity(item)，
     *      rows != 1 → BizException(409, "购物车商品已变更，请刷新后重试")。
     *   7. 返回 buildCartVo(cart)，让前端一次拿到整车最新总额。
     *
     * 事务：加 @Transactional(rollbackFor = Exception.class)，因为「读车头 + 改行」跨两次 DB 操作。
     */
    public CartVo updateCartItem(Long userId, CartItemUpdateDto dto) {
        throw new BizException(501, "功能待实现：CartService.updateCartItem");
    }

    /**
     * 删除购物车单行。
     *
     * TODO(你来实现)，步骤：
     *   1. 入参边界：userId null → 401；dto / cartItemId 为空 → BizException("购物车条目不能为空")。
     *   2. 取车头：cartRepository.getCart(userId, storeId)；
     *      为 null → 直接返回空车（用户本来就没有车，删除应该是幂等的，没必要报错）。
     *   3. 校验归属：selectByIdAndCartId(cartItemId, cart.getId())；
     *      为 null → 也直接返回当前购物车（幂等：重复点删除不应报错）。
     *   4. 删除：cartItemRepository.deleteById(cartItemId)。
     *      【不要】只按 cartItemId 删而不验归属——那样前端改个 id 就能删别人的行。
     *   5. 返回 buildCartVo(cart)。
     *
     * 不删车头：空车保留，因为 cart 有 uk_user_store 唯一键，
     * 删了下次加购又要重建，反而增加撞唯一键的概率。
     */
    public CartVo deleteCartItem(Long userId, CartItemUpdateDto dto) {
        throw new BizException(501, "功能待实现：CartService.deleteCartItem");
    }

    /**
     * 清空购物车：cartId 由服务端按 userId + storeId 查出，前端无法指定别人的车。
     */
    @Transactional(rollbackFor = Exception.class)
    public CartVo clearCart(Long userId, CartDto cartDto) {
        Long storeId = cartDto == null ? null : cartDto.getStoreId();
        checkUserAndStore(userId, storeId);

        Cart cart = cartRepository.getCart(userId, storeId);
        if (cart == null) {
            CartVo empty = new CartVo();
            empty.setUserId(userId);
            empty.setStoreId(storeId);
            empty.setCartItemList(new ArrayList<>());
            return empty;
        }
        cartItemRepository.deleteByCartId(cart.getId());
        return buildCartVo(cart);
    }

    // ------------------------------------------------------------------
    // 内部方法
    // ------------------------------------------------------------------

    private void checkUserAndStore(Long userId, Long storeId) {
        if (userId == null) {
            throw new BizException(401, "未登录或登录已失效");
        }
        if (storeId == null || storeId <= 0) {
            throw new BizException("请选择门店");
        }
    }

    private void validateItems(List<CartItemDto> items) {
        if (items == null || items.isEmpty()) {
            throw new BizException("请选择要加入购物车的商品");
        }
        if (items.size() > MAX_ITEM_KINDS) {
            throw new BizException("购物车商品种类不能超过" + MAX_ITEM_KINDS + "个");
        }
        for (CartItemDto item : items) {
            if (item == null || item.getProductId() == null || item.getProductId() <= 0) {
                throw new BizException("商品ID不合法");
            }
            if (item.getQuantity() == null || item.getQuantity() < 1) {
                throw new BizException("商品数量必须大于0");
            }
            if (item.getQuantity() > MAX_QUANTITY) {
                throw new BizException("单个商品数量不能超过" + MAX_QUANTITY + "件");
            }
            checkSnapshot(item.getCustomizationSnapshot());
        }
    }

    /**
     * 取车头，不存在则插入。
     * addCart 的 XML 配了 useGeneratedKeys，主键会回填到 cart.id，
     * 所以这里必须校验行数和 id，避免拿着 null 的 cartId 继续插明细。
     * cart 表有唯一键 uk_user_store(user_id, store_id)，并发首次加购会撞唯一键，
     * 此时回查一次即可，不能把整个请求判失败。
     */
    private Cart getOrCreateCart(Long userId, Long storeId) {
        Cart cart = cartRepository.getCart(userId, storeId);
        if (cart != null) {
            return cart;
        }
        Cart newCart = new Cart();
        newCart.setUserId(userId);
        newCart.setStoreId(storeId);
        newCart.setIsDeleted(0);
        try {
            int rows = cartRepository.addCart(newCart);
            if (rows == 1 && newCart.getId() != null) {
                return newCart;
            }
        } catch (DuplicateKeyException e) {
            Cart existing = cartRepository.getCart(userId, storeId);
            if (existing != null) {
                return existing;
            }
            throw new BizException(500, "购物车创建失败，请重试");
        }
        throw new BizException(500, "购物车创建失败");
    }

    private Map<Long, Product> loadStoreProducts(Long storeId) {
        List<Product> products = productRepository.selectProductsByStore(storeId);
        if (products == null || products.isEmpty()) {
            throw new BizException("该门店暂无可售商品");
        }
        return products.stream().collect(Collectors.toMap(Product::getId, product -> product, (left, right) -> left));
    }

    private List<CartItem> getCartItems(Long cartId) {
        List<CartItem> items = cartItemRepository.getCartItems(cartId);
        return items == null ? new ArrayList<>() : items;
    }

    /**
     * 单价优先取门店价（store_product.store_price），缺失时回退商品基础价。
     * 注意：下单侧 OrdersService 目前按 base_price 计价，接入 store_product 后两处要统一。
     */
    private BigDecimal resolveUnitPrice(Product product) {
        BigDecimal price = product.getStorePrice() != null ? product.getStorePrice() : product.getBasePrice();
        if (price == null) {
            throw new BizException("商品价格异常：" + product.getProductName());
        }
        return scale2(price);
    }

    private void checkQuantityAndStock(Product product, int quantity) {
        if (quantity < 1) {
            throw new BizException("商品数量必须大于0");
        }
        if (quantity > MAX_QUANTITY) {
            throw new BizException("单个商品数量不能超过" + MAX_QUANTITY + "件");
        }
        Integer stock = product.getStockQuantity();
        if (stock != null && quantity > stock) {
            throw new BizException("商品库存不足：" + product.getProductName());
        }
    }

    private BigDecimal checkCustomizationPrice(BigDecimal customizationPrice) {
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

    /** 行金额 = (单价 + 定制加价) * 数量，与下单侧口径保持一致 */
    private BigDecimal itemTotalPrice(BigDecimal unitPrice, BigDecimal customizationPrice, int quantity) {
        return unitPrice.add(customizationPrice)
                .multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scale2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 客制化快照校验：cart_item.customization_snapshot 在库里是 JSON 类型，
     * 传普通字符串会被 MySQL 拒绝（Invalid JSON text），必须在这里提前拦住。
     */
    private void checkSnapshot(String customizationSnapshot) {
        String snapshot = normalizeSnapshot(customizationSnapshot);
        if (snapshot == null) {
            return;
        }
        if (snapshot.length() > CUSTOMIZATION_SNAPSHOT_MAX_LENGTH) {
            throw new BizException("定制信息过长");
        }
        canonicalSnapshot(snapshot); // 顺带校验 JSON 合法性
    }

    private String normalizeSnapshot(String customizationSnapshot) {
        if (customizationSnapshot == null) {
            return null;
        }
        String trimmed = customizationSnapshot.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 合并键：同商品 + 同客制化快照视为同一行。
     * 快照列是 MySQL JSON 类型，回读时 key 会被重排、空白会被规范化（{"b":1,"a":2} 回读为 {"a": 2, "b": 1}），
     * 所以必须先把两边都转成“key 排序 + 紧凑格式”的规范字符串再比，否则同一客制化会被拆成多行。
     */
    private String mergeKey(Long productId, String customizationSnapshot) {
        return productId + "|" + canonicalSnapshot(customizationSnapshot);
    }

    private String canonicalSnapshot(String customizationSnapshot) {
        String snapshot = normalizeSnapshot(customizationSnapshot);
        if (snapshot == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(sortKeys(objectMapper.readTree(snapshot)));
        } catch (Exception e) {
            throw new BizException("定制信息必须是合法的JSON");
        }
    }

    /** 递归按 key 字典序重建 JSON，得到与 MySQL 存储顺序无关的稳定字符串 */
    private JsonNode sortKeys(JsonNode node) {
        if (node.isObject()) {
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            node.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), sortKeys(entry.getValue())));
            return objectMapper.valueToTree(sorted);
        }
        if (node.isArray()) {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            for (JsonNode child : node) {
                arrayNode.add(sortKeys(child));
            }
            return arrayNode;
        }
        return node;
    }

    private CartItem buildCartItem(Long cartId, Product product, CartItemDto itemDto,
                                   BigDecimal unitPrice, BigDecimal customizationPrice, int quantity) {
        CartItem cartItem = new CartItem();
        cartItem.setCartId(cartId);
        cartItem.setProductId(product.getId());
        cartItem.setProductName(product.getProductName());
        cartItem.setQuantity(quantity);
        cartItem.setUnitPrice(unitPrice);
        cartItem.setCustomizationPrice(customizationPrice);
        cartItem.setCustomizationSnapshot(normalizeSnapshot(itemDto.getCustomizationSnapshot()));
        cartItem.setItemTotalPrice(itemTotalPrice(unitPrice, customizationPrice, quantity));
        return cartItem;
    }

    private CartVo buildCartVo(Cart cart) {
        CartVo cartVo = new CartVo();
        cartVo.setId(cart.getId());
        cartVo.setUserId(cart.getUserId());
        cartVo.setStoreId(cart.getStoreId());
        List<CartItem> items = getCartItems(cart.getId());
        List<CartItemVo> itemVos = new ArrayList<>(items.size());
        for (CartItem item : items) {
            itemVos.add(toCartItemVo(item));
        }
        cartVo.setCartItemList(itemVos);
        return cartVo;
    }

    private CartItemVo toCartItemVo(CartItem item) {
        CartItemVo itemVo = new CartItemVo();
        itemVo.setId(item.getId());
        itemVo.setCartId(item.getCartId());
        itemVo.setProductId(item.getProductId());
        itemVo.setProductName(item.getProductName());
        itemVo.setQuantity(item.getQuantity());
        itemVo.setUnitPrice(item.getUnitPrice());
        itemVo.setCustomizationPrice(item.getCustomizationPrice());
        itemVo.setCustomizationSnapshot(item.getCustomizationSnapshot());
        itemVo.setItemTotalPrice(item.getItemTotalPrice());
        return itemVo;
    }
}
