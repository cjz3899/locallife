package com.junzhecai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.junzhecai.dto.Result;
import com.junzhecai.entity.Shop;

public interface IShopService extends IService<Shop> {

    Result saveShop(Shop shop);

    Shop queryShopById(Long id);

    Result update(Shop shop);

    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
