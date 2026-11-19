package com.whm.starbucks.controller;

import com.whm.starbucks.pojo.vo.MenuCategoryVO;
import com.whm.starbucks.pojo.vo.ResponseVo;
import com.whm.starbucks.services.MenuService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/menu")
public class MenuController {
    @Resource
    private MenuService menuService;

    // 根据商店的Id查询当前商店的菜单
    @GetMapping("/get")
    public ResponseVo<List<MenuCategoryVO>> getShopMenu(@RequestParam("storeId") Long storeId) {
        List<MenuCategoryVO> menu = menuService.getMenuByStoreId(storeId);
        return ResponseVo.success("查询成功", menu);
    }
}
