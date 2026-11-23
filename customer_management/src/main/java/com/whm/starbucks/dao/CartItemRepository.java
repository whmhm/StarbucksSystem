package com.whm.starbucks.dao;

import com.whm.starbucks.pojo.entity.CartItem;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CartItemRepository {
    /** 批量插入明细，返回值是受影响行数；主键不需要回填，落库后按 cartId 回查整车 */
    int addCartItems(@Param("items") List<CartItem> items);

    /** 取某个购物车的全部明细 */
    List<CartItem> getCartItems(@Param("cartId") Long cartId);

    /** 同商品同客制化再次加购时：更新数量、单价与行金额 */
    int updateCartItem(@Param("item") CartItem item);

    /** 清空某个购物车（cartId 来自服务端按 userId 查出的车头，天然隔离越权） */
    int deleteByCartId(@Param("cartId") Long cartId);

    // ==================================================================
    // 以下为「修改数量 / 删除单行」新增，Service 侧方法体待你实现
    // ==================================================================

    /**
     * 按「明细主键 + 购物车主键」查一行。
     *
     * 为什么必须同时带 cartId：
     * 只用明细 id 查，任何登录用户只要猜到 id 就能改/删别人的购物车行（越权）。
     * cartId 由服务端用 userId + storeId 查出，用户无法伪造，两者一起查才能隔离。
     *
     * @return null 表示该行不存在，或不属于当前用户的购物车
     */
    CartItem selectByIdAndCartId(@Param("id") Long id, @Param("cartId") Long cartId);

    /**
     * 直接改写某行的数量与行金额（整行覆盖，不做增量）。
     *
     * Service 侧必须先算好 itemTotalPrice 再传进来：
     * itemTotalPrice = (unitPrice + customizationPrice) * quantity，保留 2 位小数。
     * 不要让 SQL 里做乘法，否则与 CartService.itemTotalPrice() 的口径会分叉。
     *
     * WHERE 带 id + is_deleted = 0；返回 0 表示行已被并发删除，Service 应提示刷新重试。
     */
    int updateCartItemQuantity(@Param("item") CartItem item);

    /**
     * 删除单行（物理删除，与 deleteByCartId 保持一致）。
     *
     * 若后续要保留购物车操作痕迹，改成 UPDATE ... SET is_deleted = 1 即可，
     * 但那样 addCartItems 的「同商品合并」逻辑要先按 is_deleted = 0 过滤，注意同步。
     */
    int deleteById(@Param("id") Long id);
}
