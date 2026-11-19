package com.whm.starbucks.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {
    @GetMapping("/list")
    public String getProductList() {
        return "Product List";
    }

    @GetMapping("/category")
    public String getProductCategory() {
        return "Product Category";
    }

    @PostMapping("/add")
    public String addProduct() {
        return "Product Added";
    }
}
