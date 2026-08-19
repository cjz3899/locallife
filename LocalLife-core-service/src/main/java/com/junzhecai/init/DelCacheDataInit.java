package com.junzhecai.init;

import com.junzhecai.core.RedisKeyManage;
import com.junzhecai.entity.SeckillVoucher;
import com.junzhecai.entity.Shop;
import com.junzhecai.redis.RedisCache;
import com.junzhecai.redis.RedisKeyBuild;
import com.junzhecai.service.ISeckillVoucherService;
import com.junzhecai.service.IShopService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Order(3)
@Component
//删除缓存数据
public class DelCacheDataInit {

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private IShopService shopService;

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private SeckillVoucherLocalCache seckillVoucherLocalCache;

    @PostConstruct
    public void init() {
        log.info("==========删除缓存中的数据==========");
        List<Shop> shopList = shopService.list();
        for (final Shop shop : shopList) {
            redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_SHOP_KEY, shop.getId()));
            redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_SHOP_KEY_NULL, shop.getId()));
        }
        List<SeckillVoucher> seckillVoucherList = seckillVoucherService.list();
        for (final SeckillVoucher seckillVoucher : seckillVoucherList) {
            RedisKeyBuild seckillVoucherRedisKey =
                    RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, seckillVoucher.getVoucherId());
            seckillVoucherLocalCache.invalidate(seckillVoucherRedisKey.getRelKey());
            redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_STOCK_TAG_KEY, seckillVoucher.getVoucherId()));
            redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_USER_TAG_KEY, seckillVoucher.getVoucherId()));
            redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, seckillVoucher.getVoucherId()));
            redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_NULL_TAG_KEY, seckillVoucher.getVoucherId()));
            redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_TRACE_LOG_TAG_KEY, seckillVoucher.getVoucherId()));
            redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_USER_TAG_KEY, seckillVoucher.getVoucherId()));
            redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_ZSET_TAG_KEY, seckillVoucher.getVoucherId()));
            redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_STATUS_TAG_KEY, seckillVoucher.getVoucherId()));
        }
    }
}
