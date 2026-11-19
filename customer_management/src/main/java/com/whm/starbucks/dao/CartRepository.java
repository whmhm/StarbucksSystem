package com.whm.starbucks.dao;

import com.whm.starbucks.pojo.entity.Cart;
import org.springframework.stereotype.Repository;
import org.apache.ibatis.annotations.Param;


@Repository
public interface CartRepository {
    /** 插入车头，自增主键回填到 cart.id，返回值是受影响行数 */
    int addCart(Cart cart);

    /** 按 user_id + store_id 取未删除的车头 */
    Cart getCart(@Param("userId") Long userId, @Param("storeId") Long storeId);
}
