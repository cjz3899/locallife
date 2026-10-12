package com.junzhecai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.core.RedisKeyManage;
import com.junzhecai.dto.Result;
import com.junzhecai.entity.Shop;
import com.junzhecai.handler.BloomFilterHandlerFactory;
import com.junzhecai.mapper.ShopMapper;
import com.junzhecai.redis.RedisCache;
import com.junzhecai.redis.RedisKeyBuild;
import com.junzhecai.service.IShopService;
import com.junzhecai.servicelock.LockType;
import com.junzhecai.toolkit.SnowflakeIdGenerator;
import com.junzhecai.utils.ServiceLockTool;
import com.junzhecai.utils.SystemConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.junzhecai.constant.Constant.BLOOM_FILTER_HANDLER_SHOP;
import static com.junzhecai.utils.RedisConstants.*;

@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ServiceLockTool serviceLockTool;

    @Resource
    private RedisCache redisCache;

    @Resource
    private BloomFilterHandlerFactory bloomFilterHandlerFactory;

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;


    @Override
    public Result<Long> saveShop(final Shop shop) {
        // 写入数据库
        shop.setId(snowflakeIdGenerator.nextId());
        save(shop);
        // 写入布隆过滤器（商铺业务）
        bloomFilterHandlerFactory.get(BLOOM_FILTER_HANDLER_SHOP).add(String.valueOf(shop.getId()));
        // 返回店铺id
        return Result.ok(shop.getId());
    }

    @Override
    public Result queryById(Long id) {

        Shop shop = queryByIdV1(id);

        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        // 7.返回
        return Result.ok(shop);
    }


    private Shop queryByIdV1(Long id) {
        Shop shop =
                redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_SHOP_KEY, id), Shop.class);
        if (Objects.nonNull(shop)) {
            return shop;
        }
        log.info("查询商铺 从Redis缓存没有查询到 商铺id : {}", id);
        if (!bloomFilterHandlerFactory.get(BLOOM_FILTER_HANDLER_SHOP).contains(String.valueOf(id))) {
            log.info("查询商铺 布隆过滤器判断不存在 商铺id : {}", id);
            throw new RuntimeException("查询商铺不存在");
        }
        Boolean existResult = redisCache.hasKey(RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_SHOP_KEY_NULL, id));
        if (existResult) {
            throw new RuntimeException("查询商铺不存在");
        }
        RLock lock = serviceLockTool.getLock(LockType.Reentrant, LOCK_SHOP_KEY, new String[]{String.valueOf(id)});
        lock.lock();
        try {
            existResult = redisCache.hasKey(RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_SHOP_KEY_NULL, id));
            if (existResult) {
                throw new RuntimeException("查询商铺不存在");
            }
            shop = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_SHOP_KEY, id), Shop.class);
            if (Objects.nonNull(shop)) {
                return shop;
            }
            shop = getById(id);
            if (Objects.isNull(shop)) {
                redisCache.set(RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_SHOP_KEY_NULL, id),
                        "这是一个空值",
                        CACHE_SHOP_TTL,
                        TimeUnit.MINUTES);
                throw new RuntimeException("查询商铺不存在");
            }
            redisCache.set(RedisKeyBuild.createRedisKey(RedisKeyManage.CACHE_SHOP_KEY, id), shop,
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
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        // 1.判断是否需要根据坐标查询
        if (x == null || y == null) {
            // 不需要坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        // 2.计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        // 3.查询redis、按照距离排序、分页。结果：shopId、distance
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );
        // 4.解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if (list.size() <= from) {
            // 没有下一页了，结束
            return Result.ok(Collections.emptyList());
        }
        // 4.1.截取 from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2.获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5.根据id查询Shop
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        // 6.返回
        return Result.ok(shops);
    }
}
