package com.whm.starbucks.controller;

import com.whm.starbucks.pojo.dto.CartDto;
import com.whm.starbucks.pojo.vo.CartItemVo;
import com.whm.starbucks.pojo.vo.CartVo;
import com.whm.starbucks.pojo.vo.ResponseVo;
import com.whm.starbucks.services.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/car")
@RequiredArgsConstructor
public class CartController {
    @Autowired
    private CartService cartService;
    /**
     * 所有接口从 @AuthenticationPrincipal Long userId 取用户，不信任请求体里的 userId。
     * 加入购物车
     * 查看购物车
     * 修改数量
     * 清空购物车
     * @return
     */

    // 购物车列表
    @GetMapping("/list")
    public ResponseVo<CartVo> getCarList(@AuthenticationPrincipal Long userId, @RequestParam Long storeId) {
        CartVo cartVo = cartService.getCart(userId, storeId);
        return ResponseVo.success("购物车列表", cartVo);
    }
    @PostMapping("/add/item")
    public ResponseVo<CartVo> addCartItem(@AuthenticationPrincipal Long userId, @RequestBody CartDto cartDto) {
        CartVo cartVo = cartService.addCartItem(userId, cartDto);
        return ResponseVo.success("添加成功", cartVo);
    }

//    @PostMapping("/update/item")
//    public ResponseVo<CartVo> updateCartItem(@AuthenticationPrincipal Long userId, @RequestBody CartDto cartDto) {
//        CartVo  cartVo = cartService.updateCartItem(userId, cartDto.getStoreId(), cartDto.getProductId(), cartDto.getQuantity());
//        return ResponseVo.success("修改成功", cartVo);
//    }

//    @PostMapping("/delete/item")
//    public ResponseVo<CartVo> deleteCartItem(@AuthenticationPrincipal Long userId, @RequestBody CartDto cartDto) {
//        CartVo cartVo = cartService.deleteCartItem(userId, cartDto.getStoreId(), cartDto.getProductId());
//        return ResponseVo.success("删除成功", cartVo);
//    }

    @PostMapping("/clear")
    public ResponseVo<CartVo> clearCart(@AuthenticationPrincipal Long userId, @RequestBody CartDto cartDto) {
        CartVo cartVo = cartService.clearCart(userId, cartDto);
        return ResponseVo.success("清空成功", cartVo);
    }


}
