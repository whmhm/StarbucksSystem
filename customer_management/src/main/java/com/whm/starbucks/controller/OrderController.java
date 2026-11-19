package com.whm.starbucks.controller;

import com.whm.starbucks.pojo.dto.OrdersDto;
import com.whm.starbucks.pojo.entity.Orders;
import com.whm.starbucks.pojo.vo.OrderVo;
import com.whm.starbucks.pojo.vo.ResponseVo;
import com.whm.starbucks.services.OrdersService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {
    @Autowired
    private OrdersService ordersService;

    @PostMapping("/add")
    public ResponseVo<OrderVo> addOrder(@AuthenticationPrincipal Long userId, @RequestBody OrdersDto ordersDto) {
        OrderVo orderVo = ordersService.addOrder(userId, ordersDto);
//        orderItem 拆分到订单项服务
        return ResponseVo.success("添加成功", orderVo);
    }
    public ResponseVo<List<OrderVo>> getOrdersByUserPhone(@RequestParam String phone) {
        List<OrderVo> orderVoList = ordersService.getOrdersByUserPhone(phone);
        return ResponseVo.success("查询成功", orderVoList);
    }

    public ResponseVo<OrderVo> getOrderByNo(@RequestParam String orderNo) {
        OrderVo orderVo = ordersService.getOrderByNo(orderNo);
        return ResponseVo.success("查询成功", orderVo);
    }
}
