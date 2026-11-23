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

    /**
     * 查某笔订单的全部明细。
     *
     * 注意：selectOrderItemsByOrderId 的 SQL 早就写在 OrderItemRepository.xml 里了，
     * 但接口方法一直没声明，所以 Service 调不到 → OrderVo.items 永远是 null。
     * 补上这个签名后，XML 里那条 statement 才会被绑定。【本行仅需补签名，XML 不用改】
     *
     * 若后续要支持「已删除明细」，记得在 XML 的 WHERE 里补 is_deleted = 0（现在没有这个条件）。
     */
    List<OrderItem> selectOrderItemsByOrderId(@Param("orderId") Long orderId);
}
