package com.junzhecai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import com.junzhecai.dto.Result;
import com.junzhecai.entity.Shop;
import com.junzhecai.mapper.ShopMapper;
import com.junzhecai.service.IShopService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    
    
    
    @Override
    public Result<Long> saveShop(final Shop shop) {
        return null;
    }
    
    @Override
    public Result queryById(Long id) {
        return null;
    }
    
    public Shop queryByIdV1(Long id){
        return null;
    }
    
    public Shop queryByIdV2(Long id){
        return null;
    }
    
    public Shop queryByIdV3(Long id){
        return null;
    }
    
    public Shop queryByIdV4(Long id){
        return null;
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        return null;
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        return null;
    }
}
