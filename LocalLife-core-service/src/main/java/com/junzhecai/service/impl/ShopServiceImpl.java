package com.junzhecai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.core.RedisKeyManage;
import com.junzhecai.dto.Result;
import com.junzhecai.entity.Shop;
import com.junzhecai.exception.LocalLifeFrameException;
import com.junzhecai.handler.BloomFilterHandlerFactory;
import com.junzhecai.mapper.ShopMapper;
import com.junzhecai.redis.RedisCache;
import com.junzhecai.redis.RedisKeyBuild;
import com.junzhecai.service.IShopService;
import com.junzhecai.servicelock.LockType;
import com.junzhecai.utils.ServiceLockTool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.junzhecai.constant.Constant.BLOOM_FILTER_HANDLER_SHOP;
import static com.junzhecai.utils.RedisConstants.CACHE_SHOP_TTL;
import static com.junzhecai.utils.RedisConstants.LOCK_SHOP_KEY;

@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private RedisCache redisCache;
    @Resource
    private BloomFilterHandlerFactory bloomFilterHandlerFactory;
    @Resource
    private ServiceLockTool serviceLockTool;

    @Override
    public Result<Long> saveShop(final Shop shop) {
        return null;
    }

    @Override
    public Shop queryShopById(Long id) {
        Shop shop = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_SHOP_KEY, id), Shop.class);
        if (Objects.nonNull(shop)) {
            return shop;
        }
        log.info("查询商铺 从Redis缓存没有查询到 商铺id：{}", id);
        if (!bloomFilterHandlerFactory.get(BLOOM_FILTER_HANDLER_SHOP).contains(String.valueOf(id))) {
            log.info("查询商铺 布隆过滤器判断不存在 商铺id：{}", id);
            throw new LocalLifeFrameException("商铺不存在");
        }
        Boolean existResult = redisCache.hasKey(RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_SHOP_KEY_NULL, id));
        if (existResult) {
            throw new LocalLifeFrameException("商铺不存在");
        }
        RLock lock = serviceLockTool.getLock(
                LockType.Reentrant,
                LOCK_SHOP_KEY,
                new String[]{String.valueOf(id)}
        );
        lock.lock();
        try {
            existResult = redisCache.hasKey(RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_SHOP_KEY_NULL, id));
            if (existResult) {
                throw new LocalLifeFrameException("商铺不存在");
            }
            shop = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_SHOP_KEY, id), Shop.class);
            if (Objects.nonNull(shop)) {
                return shop;
            }
            shop = getById(id);
            if (Objects.isNull(shop)) {
                redisCache.set(RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_SHOP_KEY_NULL, id),
                        "空值",
                        CACHE_SHOP_TTL,
                        TimeUnit.MINUTES);
                throw new LocalLifeFrameException("商铺不存在");
            }
            redisCache.set(RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_SHOP_KEY, id),
                    shop,
                    CACHE_SHOP_TTL,
                    TimeUnit.MINUTES);
            return shop;
        } finally {
            lock.unlock();
        }

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
