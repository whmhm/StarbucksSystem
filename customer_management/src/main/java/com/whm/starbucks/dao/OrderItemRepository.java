package com.whm.starbucks.dao;

import com.whm.starbucks.pojo.entity.OrderItem;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository {
    int insertOrderItem(@Param("orderItem")OrderItem orderItem);

    /** 批量插入订单明细，须先绑定 orderId */
    int insertOrderItems(@Param("items") List<OrderItem> items);
}
