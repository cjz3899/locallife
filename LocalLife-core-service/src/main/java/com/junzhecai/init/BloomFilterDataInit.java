package com.junzhecai.init;

import com.junzhecai.entity.SeckillVoucher;
import com.junzhecai.entity.Shop;
import com.junzhecai.handler.BloomFilterHandlerFactory;
import com.junzhecai.service.ISeckillVoucherService;
import com.junzhecai.service.IShopService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.junzhecai.constant.Constant.BLOOM_FILTER_HANDLER_SHOP;
import static com.junzhecai.constant.Constant.BLOOM_FILTER_HANDLER_VOUCHER;

@Slf4j
@Order(1)
@Component
//布隆过滤器初始化
public class BloomFilterDataInit {

    @Autowired
    private IShopService shopService;

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private BloomFilterHandlerFactory bloomFilterHandlerFactory;

    @PostConstruct
    public void init() {
        log.info("==========初始化商铺的布隆过滤器==========");
        List<Shop> shopList = shopService.list();
        for (Shop shop : shopList) {
            bloomFilterHandlerFactory.get(BLOOM_FILTER_HANDLER_SHOP).add(String.valueOf(shop.getId()));
        }
        log.info("==========初始化优惠券的布隆过滤器==========");
        List<SeckillVoucher> seckillVoucherlist = seckillVoucherService.list();
        for (SeckillVoucher seckillVoucher : seckillVoucherlist) {
            bloomFilterHandlerFactory.get(BLOOM_FILTER_HANDLER_VOUCHER).add(String.valueOf(seckillVoucher.getVoucherId()));
        }
    }
}