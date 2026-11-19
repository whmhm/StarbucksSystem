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
}
