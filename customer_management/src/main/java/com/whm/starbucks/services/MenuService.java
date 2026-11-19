package com.whm.starbucks.services;

import com.whm.starbucks.exceptions.BizException;
import com.whm.starbucks.pojo.vo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MenuService {
    @Autowired
    private ProductService productService;
    @Autowired
    private ProductCategoryService productCategoryService;
    @Autowired
    private StoreService storeService;

    /**
     * 菜单查询
     * 1、校验店铺是否存在
     * 2、查询菜单
     *      2.1、菜单存在返回菜单
     *
     *      2.2、菜单不存在返回空
     *
     * 返回结构：[{categoryId, categoryName, sortOrder, products:[{productId, productName, ...}]}]
     */
    public List<MenuCategoryVO> getMenuByStoreId(Long storeId) {
        // 1、校验店铺是否存在
        StoreVo store = storeService.getStoreByStoreId(storeId);
        if (store == null) {
            throw new BizException(404, "店铺不存在");
        }
        // 2、查分类，并按 sortOrder 排序（空菜单与完整菜单共用同一顺序）
        List<ProductCategoryVo> categories = productCategoryService.getProductCategory().stream()
                .sorted(Comparator.comparing(ProductCategoryVo::getSortOrder,
                        Comparator.nullsLast(Integer::compareTo)))
                .collect(Collectors.toList());

        // 3、查门店可售商品，categoryId 是后续分组的连接键
        List<ProductVo> products = productService.getProductsByStore(storeId);

        // 4、商品为空：只返回分类骨架，products 为空 List
        if (products.isEmpty()) {
            return categories.stream().map(this::toEmptyCategory).collect(Collectors.toList());
        }

        // 5、按 categoryId 分组，key 必须是 Long，否则和 c.getId() 对不上
        Map<Long, List<ProductVo>> productGroups = products.stream()
                .collect(Collectors.groupingBy(ProductVo::getCategoryId));

        // 6、遍历分类装配商品：先出骨架，再挂商品；取不到分组用 emptyList 兜底
        return categories.stream().map(c -> {
            MenuCategoryVO categoryVO = toEmptyCategory(c);

            List<ProductVo> categoryProducts = productGroups.getOrDefault(c.getId(), Collections.emptyList());

            // ProductVo -> MenuProductVO
            List<MenuProductVO> productVOs = categoryProducts.stream().map(p -> {
                MenuProductVO pv = new MenuProductVO();
                pv.setProductId(p.getId());
                pv.setProductName(p.getProductName());
                pv.setDescription(p.getDescription());
                pv.setImageUrl(p.getImageUrl());
                // 优先取门店价，缺失时回退基础价
                pv.setPrice(p.getStorePrice() != null ? p.getStorePrice() : p.getBasePrice());
                pv.setStockQuantity(p.getStockQuantity());
                pv.setIsAvailable(p.getIsAvailable());
                return pv;
            }).collect(Collectors.toList());

            categoryVO.setProducts(productVOs);

            return categoryVO;
        }).collect(Collectors.toList());
    }

    /**
     * 构建「空分类节点」：只填分类自身的信息，products 给空 List（不要留 null，前端遍历会崩）
     */
    public MenuCategoryVO toEmptyCategory(ProductCategoryVo c) {
        MenuCategoryVO menuCategoryVO = new MenuCategoryVO();
        menuCategoryVO.setCategoryId(c.getId());
        menuCategoryVO.setCategoryName(c.getCategoryName());
        menuCategoryVO.setSortOrder(c.getSortOrder());
        menuCategoryVO.setProducts(new ArrayList<>());
        return menuCategoryVO;
    }
}
