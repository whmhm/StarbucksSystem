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
}
