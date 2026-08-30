package com.junzhecai.controller;

import com.junzhecai.dto.Result;
import com.junzhecai.entity.Shop;
import com.junzhecai.service.IShopService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/shop")
public class ShopController {
    @Resource
    private IShopService shopService;

    @GetMapping("/{id}")
    public Result<Shop> queryShopById(@PathVariable("id") Long id) {
        return Result.ok(shopService.queryShopById(id));
    }


}
