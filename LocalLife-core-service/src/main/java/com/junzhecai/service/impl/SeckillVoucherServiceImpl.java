package com.junzhecai.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.cache.BloomFilterHealthGuard;
import com.junzhecai.cache.SeckillVoucherLocalCache;
import com.junzhecai.core.RedisKeyManage;
import com.junzhecai.entity.SeckillVoucher;
import com.junzhecai.entity.Voucher;
import com.junzhecai.exception.LocalLifeFrameException;
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

import static com.junzhecai.constant.DistributedLockConstants.UPDATE_SECKILL_VOUCHER_LOCK;
import static com.junzhecai.constant.DistributedLockConstants.UPDATE_SECKILL_VOUCHER_STOCK_LOCK;
import static com.junzhecai.utils.RedisConstants.CACHE_NULL_TTL;
import static com.junzhecai.utils.RedisConstants.LOCK_SECKILL_VOUCHER_STOCK_KEY;

@Slf4j
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {

    /**
     * 重建缓存时锁的租约时长（秒）
     * 显式指定租约后Redisson不再启用看门狗续期，锁到期自动释放，避免持锁线程异常时死锁
     */
    private static final long LOCK_LEASE_SECONDS = 30L;

    /**
     * 抢锁失败后的自旋次数与间隔：4 × 50ms = 最多等待200ms
     */
    private static final int LOCK_SPIN_TIMES = 4;
    private static final long LOCK_SPIN_INTERVAL_MS = 50L;

    /**
     * 活动已结束后写入Redis的固定TTL（秒），终态数据不再每次回源
     */
    private static final long ENDED_CACHE_TTL_SECONDS = 300L;

    @Resource
    private RedisCache redisCache;
    @Resource
    private ServiceLockTool serviceLockTool;
    @Resource
    private BloomFilterHealthGuard bloomFilterHealthGuard;
    @Resource
    private SeckillVoucherLocalCache seckillVoucherLocalCache;
    @Resource
    private IVoucherService voucherService;
    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;

    /**
     * 固定流程：
     * 1.查本地缓存（纯内存，不引入任何分布式锁）
     * 2.查Redis
     * 3.布隆过滤器前置拦截
     * 4.空值缓存拦截
     * 5.重建临界区加锁回源数据库（单飞 + 抢不到锁降级）
     * 说明：本方法刻意不再使用 @ServiceLock(Read)。
     * 分布式读锁会把"本地缓存命中"这条最快路径也变成两次Redis往返，直接抵消L1的存在意义；
     * 并发保护下沉到重建临界区，并与"删除缓存"共用同一把写锁。
     *
     */
    @Override
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
        //通过布隆过滤器判断是否存在（访问异常或已降级时放行，交给后续空值缓存与数据库兜底）
        if (bloomFilterHealthGuard.mightExist(voucherId)) {
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
        //加锁解决缓存击穿：只放一个请求回源，其余请求走降级
        //使用与"删除缓存"同名的写锁，使"重建"与"删除"互斥，避免旧值被写回后长期滞留
        RLock lock = serviceLockTool.getLock(LockType.Write, UPDATE_SECKILL_VOUCHER_LOCK, new String[]{String.valueOf(voucherId)});
        boolean locked;
        try {
            //不等待：抢不到锁说明已有线程在重建，立即走降级路径，避免业务线程排队堆积
            locked = lock.tryLock(0, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LocalLifeFrameException("系统繁忙，请稍后重试");
        }
        if (!locked) {
            return degradeWhileRebuilding(voucherId, seckillVoucherRedisKey, seckillVoucherNullRedisKey);
        }
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
            //活动进行中：TTL取距离活动结束时间的秒数
            //活动已结束：属于终态数据，给固定TTL，避免TTL恒为1秒导致每次请求都回源数据库
            long remainSeconds = LocalDateTimeUtil.between(LocalDateTimeUtil.now(), seckillVoucher.getEndTime()).toSeconds();
            long ttlSeconds = remainSeconds > 0 ? remainSeconds : ENDED_CACHE_TTL_SECONDS;
            Voucher voucher = voucherService.lambdaQuery().eq(Voucher::getId, voucherId).one();
            seckillVoucherFullModel = new SeckillVoucherFullModel();
            BeanUtils.copyProperties(seckillVoucher, seckillVoucherFullModel);
            seckillVoucherFullModel.setShopId(voucher.getShopId());
            seckillVoucherFullModel.setStatus(voucher.getStatus());
            redisCache.set(seckillVoucherRedisKey, seckillVoucherFullModel, ttlSeconds, TimeUnit.SECONDS);
            //同步写入本地缓存
            seckillVoucherLocalCache.put(seckillVoucherRedisKey.getRelKey(), seckillVoucherFullModel);
            return seckillVoucherFullModel;
        } finally {
            try {
                lock.unlock();
            } catch (Exception e) {
                //锁租约到期后会被自动释放，此时unlock可能失败，不能因此影响正常返回
                log.warn("释放秒杀券重建锁失败 秒杀优惠券id : {}", voucherId, e);
            }
        }
    }

    /**
     * 抢锁失败时的降级处理
     * 抢到锁的线程正在重建缓存，重建通常几十毫秒即可完成，因此这里短暂自旋等待，
     * 尽量让锁竞争对用户透明；自旋有明确上限，避免请求长时间挂住把Tomcat线程池占满。
     *
     */
    private SeckillVoucherFullModel degradeWhileRebuilding(Long voucherId,
                                                           RedisKeyBuild seckillVoucherRedisKey,
                                                           RedisKeyBuild seckillVoucherNullRedisKey) {
        for (int i = 0; i < LOCK_SPIN_TIMES; i++) {
            try {
                TimeUnit.MILLISECONDS.sleep(LOCK_SPIN_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            SeckillVoucherFullModel value = seckillVoucherLocalCache.get(seckillVoucherRedisKey.getRelKey());
            if (Objects.nonNull(value)) {
                return value;
            }
            value = redisCache.get(seckillVoucherRedisKey, SeckillVoucherFullModel.class);
            if (Objects.nonNull(value)) {
                seckillVoucherLocalCache.put(seckillVoucherRedisKey.getRelKey(), value);
                return value;
            }
            if (Boolean.TRUE.equals(redisCache.hasKey(seckillVoucherNullRedisKey))) {
                throw new LocalLifeFrameException("秒杀优惠券不存在");
            }
        }
        log.warn("等待缓存重建超时 秒杀优惠券id : {}", voucherId);
        throw new LocalLifeFrameException("系统繁忙，请稍后重试");
    }

    @Override
    @ServiceLock(lockType = LockType.Read, name = UPDATE_SECKILL_VOUCHER_STOCK_LOCK, keys = {"#voucherId"})
    public void loadVoucherStock(Long voucherId) {
        if (bloomFilterHealthGuard.mightExist(voucherId)) {
            log.info("加载库存 布隆过滤器判断不存在 秒杀优惠券id : {}", voucherId);
            throw new LocalLifeFrameException("秒杀优惠券不存在");
        }
        String stock = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_STOCK_TAG_KEY, voucherId), String.class);
        if (Objects.isNull(stock)) {
            return;
        }
        RLock lock = serviceLockTool.getLock(LockType.Reentrant, LOCK_SECKILL_VOUCHER_STOCK_KEY, new String[]{String.valueOf(voucherId)});
        lock.lock();
        try {
            stock = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_STOCK_TAG_KEY, voucherId), String.class);
            if (Objects.isNull(stock)) {
                return;
            }
            SeckillVoucher seckillVoucher = query().eq("voucher_id", voucherId).one();
            if (Objects.nonNull(seckillVoucher)) {
                //活动进行中取距离结束的秒数；活动已结束属于终态，给固定TTL避免反复回源
                long remainStockSeconds = LocalDateTimeUtil.between(LocalDateTimeUtil.now(), seckillVoucher.getEndTime()).getSeconds();
                long ttlSeconds = remainStockSeconds > 0 ? remainStockSeconds : ENDED_CACHE_TTL_SECONDS;
                redisCache.set(
                        RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_STOCK_TAG_KEY, voucherId),
                        String.valueOf(seckillVoucher.getStock()),
                        ttlSeconds,
                        TimeUnit.SECONDS
                );
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackStock(final Long voucherId) {
        return seckillVoucherMapper.rollbackStock(voucherId) > 0;
    }
}
