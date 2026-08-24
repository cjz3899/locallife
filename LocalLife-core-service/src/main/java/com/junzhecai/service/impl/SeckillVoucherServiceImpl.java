package com.junzhecai.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.cache.SeckillVoucherLocalCache;
import com.junzhecai.core.RedisKeyManage;
import com.junzhecai.entity.SeckillVoucher;
import com.junzhecai.entity.Voucher;
import com.junzhecai.exception.LocalLifeFrameException;
import com.junzhecai.handler.BloomFilterHandlerFactory;
import com.junzhecai.mapper.SeckillVoucherMapper;
import com.junzhecai.model.SeckillVoucherFullModel;
import com.junzhecai.redis.RedisCache;
import com.junzhecai.redis.RedisKeyBuild;
import com.junzhecai.service.ISeckillVoucherService;
import com.junzhecai.service.IVoucherService;
import com.junzhecai.servicelock.LockType;
import com.junzhecai.servicelock.annotion.ServiceLock;
import com.junzhecai.utils.ServiceLockTool;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.junzhecai.constant.Constant.BLOOM_FILTER_HANDLER_VOUCHER;
import static com.junzhecai.constant.DistributedLockConstants.UPDATE_SECKILL_VOUCHER_LOCK;
import static com.junzhecai.utils.RedisConstants.CACHE_NULL_TTL;
import static com.junzhecai.utils.RedisConstants.LOCK_SECKILL_VOUCHER_KEY;

@Slf4j
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {
    @Resource
    private RedisCache redisCache;
    @Resource
    private ServiceLockTool serviceLockTool;
    @Resource
    private BloomFilterHandlerFactory bloomFilterHandlerFactory;
    @Resource
    private SeckillVoucherLocalCache seckillVoucherLocalCache;
    @Resource
    private IVoucherService voucherService;

    /**
     * 固定流程：
     * 1.查本地缓存
     * 2.查Redis
     * 3.查数据库
     */
    @Override
    @ServiceLock(lockType = LockType.Read, name = UPDATE_SECKILL_VOUCHER_LOCK, keys = {"#voucherId"})
    public SeckillVoucherFullModel queryByVoucherId(Long voucherId) {
        RedisKeyBuild seckillVoucherRedisKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, voucherId);
        RedisKeyBuild seckillVoucherNullRedisKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_NULL_TAG_KEY, voucherId);
        //先查本地缓存
        SeckillVoucherFullModel localCacheHit = seckillVoucherLocalCache.get(seckillVoucherRedisKey.getRelKey());
        if (Objects.nonNull(localCacheHit)) {
            return localCacheHit;
        }
        //双重检测解决缓存击穿
        SeckillVoucherFullModel seckillVoucherFullModel = redisCache.get(seckillVoucherRedisKey,
                SeckillVoucherFullModel.class);
        if (Objects.nonNull(seckillVoucherFullModel)) {
            //写入本地缓存，加快后续访问
            seckillVoucherLocalCache.put(seckillVoucherRedisKey.getRelKey(), seckillVoucherFullModel);
            return seckillVoucherFullModel;
        }
        log.info("查询秒杀优惠券 从Redis缓存没有查询到 秒杀优惠券的优惠券id : {}", voucherId);
        //通过布隆过滤器判断是否存在
        if (!bloomFilterHandlerFactory.get(BLOOM_FILTER_HANDLER_VOUCHER).contains(String.valueOf(voucherId))) {
            log.info("查询秒杀优惠券 布隆过滤器判断不存在 秒杀优惠券id : {}", voucherId);
            throw new LocalLifeFrameException("秒杀优惠券不存在");
        }
        //解决缓存穿透，从本地缓存中判断是否存在优惠券空值信息，如果有，代表优惠券不存在，直接返回
        SeckillVoucherFullModel seckillVoucherFullModelNotExist = seckillVoucherLocalCache.get(seckillVoucherNullRedisKey.getRelKey());
        if (Objects.nonNull(seckillVoucherFullModelNotExist)) {
            throw new LocalLifeFrameException("秒杀优惠券不存在");
        }
        //解决缓存穿透，从缓存中判断是否存在优惠券空值信息
        Boolean existResult = redisCache.hasKey(seckillVoucherNullRedisKey);
        if (existResult) {
            throw new LocalLifeFrameException("秒杀优惠券不存在");
        }
        //实现双重检测
        //加锁，解决缓存击穿
        RLock lock = serviceLockTool.getLock(LockType.Reentrant, LOCK_SECKILL_VOUCHER_KEY, new String[]{String.valueOf(voucherId)});
        lock.lock();
        try {
            //再次从本地缓存中获取优惠券信息，通过此步骤可以避免大量请求在获取锁后，直接访问Redis或者数据库
            localCacheHit = seckillVoucherLocalCache.get(seckillVoucherRedisKey.getRelKey());
            if (Objects.nonNull(localCacheHit)) {
                return localCacheHit;
            }
            //再次从Redis中获取优惠券信息，通过此步骤可以避免大量请求在获取锁后，直接击穿缓存访问数据库
            seckillVoucherFullModel = redisCache.get(seckillVoucherRedisKey, SeckillVoucherFullModel.class);
            if (Objects.nonNull(seckillVoucherFullModel)) {
                //同样写入本地缓存，加快后续访问
                seckillVoucherLocalCache.put(seckillVoucherRedisKey.getRelKey(), seckillVoucherFullModel);
                return seckillVoucherFullModel;
            }
            //再次从本地缓存中判断是否存在优惠券空值信息，如果有，代表优惠券不存在，直接返回
            seckillVoucherFullModelNotExist = seckillVoucherLocalCache.get(seckillVoucherNullRedisKey.getRelKey());
            if (Objects.nonNull(seckillVoucherFullModelNotExist)) {
                throw new LocalLifeFrameException("秒杀优惠券不存在");
            }
            //再次从Redis中判断是否存在优惠券空值信息，如果有，代表优惠券不存在，直接返回
            existResult = redisCache.hasKey(seckillVoucherNullRedisKey);
            if (existResult) {
                throw new LocalLifeFrameException("秒杀优惠券不存在");
            }
            //缓存中不存在，查询数据库
            SeckillVoucher seckillVoucher = getOne(new QueryWrapper<SeckillVoucher>().eq("voucher_id", voucherId));
            if (Objects.isNull(seckillVoucher)) {
                //如果从数据库查询是空的，将空值写入本地缓存
                seckillVoucherFullModel = new SeckillVoucherFullModel();
                seckillVoucherFullModel.setEndTime(LocalDateTimeUtil.offset(LocalDateTimeUtil.now(), CACHE_NULL_TTL, ChronoUnit.MINUTES));
                seckillVoucherLocalCache.put(seckillVoucherNullRedisKey.getRelKey(), seckillVoucherFullModel);
                //如果从数据库查询是空的，将空值写入redis
                redisCache.set(seckillVoucherNullRedisKey,
                        "空值",
                        CACHE_NULL_TTL,
                        TimeUnit.MINUTES);
                throw new LocalLifeFrameException("秒杀优惠券不存在");
            }
            //TTL为距离活动结束时间的秒数
            long ttlSeconds = Math.max(LocalDateTimeUtil.between(LocalDateTimeUtil.now(), seckillVoucher.getEndTime()).toSeconds(), 1L);
            Voucher voucher = voucherService.getOne(new QueryWrapper<Voucher>().eq("voucher_id", voucherId));
            seckillVoucherFullModel = new SeckillVoucherFullModel();
            BeanUtils.copyProperties(seckillVoucher, seckillVoucherFullModel);
            seckillVoucherFullModel.setShopId(voucher.getShopId());
            seckillVoucherFullModel.setStatus(voucher.getStatus());
            seckillVoucherFullModel.setStock(voucher.getStock());
            redisCache.set(seckillVoucherRedisKey, seckillVoucherFullModel, ttlSeconds, TimeUnit.SECONDS);
            //同步写入本地缓存
            seckillVoucherLocalCache.put(seckillVoucherRedisKey.getRelKey(), seckillVoucherFullModel);
            return seckillVoucherFullModel;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void loadVoucherStock(Long voucherId) {
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackStock(final Long voucherId) {
        return false;
    }
}
