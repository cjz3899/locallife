package com.junzhecai.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.cache.SeckillVoucherCacheInvalidationPublisher;
import com.junzhecai.core.RedisKeyManage;
import com.junzhecai.dto.*;
import com.junzhecai.entity.SeckillVoucher;
import com.junzhecai.entity.Voucher;
import com.junzhecai.enums.BaseCode;
import com.junzhecai.enums.StockUpdateType;
import com.junzhecai.enums.SubscribeStatus;
import com.junzhecai.exception.LocalLifeFrameException;
import com.junzhecai.mapper.VoucherMapper;
import com.junzhecai.redis.RedisCache;
import com.junzhecai.redis.RedisKeyBuild;
import com.junzhecai.service.ISeckillVoucherService;
import com.junzhecai.service.IVoucherOrderService;
import com.junzhecai.service.IVoucherService;
import com.junzhecai.servicelock.LockType;
import com.junzhecai.servicelock.annotion.ServiceLock;
import com.junzhecai.utils.UserHolder;
import com.junzhecai.vo.GetSubscribeStatusVo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.junzhecai.constant.DistributedLockConstants.UPDATE_SECKILL_VOUCHER_STOCK_LOCK;
import static com.junzhecai.service.impl.VoucherOrderServiceImpl.SECKILL_ORDER_EXECUTOR;

@Slf4j
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private SeckillVoucherCacheInvalidationPublisher seckillVoucherCacheInvalidationPublisher;
    @Resource
    private RedisCache redisCache;
    @Resource
    private IVoucherOrderService voucherOrderService;
    @Value("${seckill.reminder.ahead.seconds:120}")
    private long reminderAheadSeconds;

    @Override
    public Long addVoucher(VoucherDto voucherDto) {
        return null;
    }

    @Override
    public Result<List<Voucher>> queryVoucherOfShop(Long shopId) {
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addSeckillVoucher(SeckillVoucherDto seckillVoucherDto) {
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSeckillVoucher(UpdateSeckillVoucherDto updateSeckillVoucherDto) {
        Long voucherId = updateSeckillVoucherDto.getVoucherId();
        //更新tb_voucher表的非空字段
        boolean updatedVoucher = false;
        UpdateWrapper<Voucher> voucherWrapper = new UpdateWrapper<Voucher>().eq("id", voucherId);
        for (Map.Entry<String, Function<UpdateSeckillVoucherDto, Object>> entry : VOUCHER_FIELD_MAPPING.entrySet()) {
            Object value = entry.getValue().apply(updateSeckillVoucherDto);
            if (value != null) {
                voucherWrapper.set(entry.getKey(), value);
                updatedVoucher = true;
            }
        }
        if (updatedVoucher) {
            voucherWrapper.set("update_time", LocalDateTimeUtil.now());
            update(voucherWrapper);
        }

        //更新 tb_seckill_voucher 表的非空字段（仅时间相关）
        boolean updatedSeckill = false;
        UpdateWrapper<SeckillVoucher> seckillWrapper = new UpdateWrapper<SeckillVoucher>().eq("voucher_id", voucherId);
        for (Map.Entry<String, Function<UpdateSeckillVoucherDto, Object>> entry : SECKILL_VOUCHER_FIELD_MAPPING.entrySet()) {
            Object value = entry.getValue().apply(updateSeckillVoucherDto);
            if (value != null) {
                seckillWrapper.set(entry.getKey(), value);
                updatedSeckill = true;
            }
        }
        if (updatedSeckill) {
            seckillWrapper.set("update_time", LocalDateTimeUtil.now());
            seckillVoucherService.update(seckillWrapper);
        }
        //更新后清理缓存，等待读路径按新数据重建缓存
        if (updatedVoucher || updatedSeckill) {
            seckillVoucherCacheInvalidationPublisher.publishInvalidate(voucherId, "update");
        }
    }

    //字段映射
    private static final Map<String, Function<UpdateSeckillVoucherDto, Object>> VOUCHER_FIELD_MAPPING = Map.of(
            "title", UpdateSeckillVoucherDto::getTitle,
            "sub_title", UpdateSeckillVoucherDto::getSubTitle,
            "rules", UpdateSeckillVoucherDto::getRules,
            "pay_value", UpdateSeckillVoucherDto::getPayValue,
            "actual_value", UpdateSeckillVoucherDto::getActualValue,
            "type", UpdateSeckillVoucherDto::getType,
            "status", UpdateSeckillVoucherDto::getStatus
    );

    private static final Map<String, Function<UpdateSeckillVoucherDto, Object>> SECKILL_VOUCHER_FIELD_MAPPING = Map.of(
            "begin_time", UpdateSeckillVoucherDto::getBeginTime,
            "end_time", UpdateSeckillVoucherDto::getEndTime,
            "allowed_levels", UpdateSeckillVoucherDto::getAllowedLevels,
            "min_level", UpdateSeckillVoucherDto::getMinLevel
    );

    @Override
    @Transactional(rollbackFor = Exception.class)
    @ServiceLock(lockType = LockType.Write, name = UPDATE_SECKILL_VOUCHER_STOCK_LOCK, keys = {"#updateSeckillVoucherStockDto.voucherId"})
    public void updateSeckillVoucherStock(UpdateSeckillVoucherStockDto updateSeckillVoucherStockDto) {
        SeckillVoucher seckillVoucher = seckillVoucherService.query().eq("voucher_id", updateSeckillVoucherStockDto.getVoucherId()).one();
        if (Objects.isNull(seckillVoucher)) {
            throw new LocalLifeFrameException(BaseCode.SECKILL_VOUCHER_NOT_EXIST);
        }
        Integer oldStock = seckillVoucher.getStock();
        Integer oldInitStock = seckillVoucher.getInitStock();
        Integer newInitStock = updateSeckillVoucherStockDto.getInitStock();
        int changeStock = newInitStock - oldInitStock;
        if (changeStock == 0) {
            return;
        }
        int newStock = oldStock + changeStock;
        if (newStock < 0) {
            throw new LocalLifeFrameException(BaseCode.AFTER_SECKILL_VOUCHER_REMAIN_STOCK_NOT_NEGATIVE_NUMBER);
        }
        StockUpdateType stockUpdateType = StockUpdateType.INCREASE;
        if (changeStock < 0) {
            stockUpdateType = StockUpdateType.DECREASE;
        }
        UpdateWrapper<SeckillVoucher> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("voucher_id", seckillVoucher.getVoucherId())
                .set("stock", newStock)
                .set("init_stock", newInitStock)
                .set("update_time", LocalDateTimeUtil.now());
        seckillVoucherService.update(updateWrapper);
        String oldRedisStockStr = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, seckillVoucher.getVoucherId()), String.class);
        Integer newRedisStock = null;
        if (StrUtil.isBlank(oldRedisStockStr)) {
            redisCache.set(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_STOCK_TAG_KEY, seckillVoucher.getVoucherId()), String.valueOf(newStock));
        } else {
            int oldRedisStock = Integer.parseInt(oldRedisStockStr);
            newRedisStock = oldRedisStock + changeStock;
            if (newRedisStock < 0) {
                throw new LocalLifeFrameException(BaseCode.AFTER_SECKILL_VOUCHER_REMAIN_STOCK_NOT_NEGATIVE_NUMBER);
            }
            redisCache.set(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_STOCK_TAG_KEY, seckillVoucher.getVoucherId()), String.valueOf(newRedisStock));
            log.info("修改库存成功！修改库存类型：{},修改前：数据库初始库存：{},redis旧库存：{},修改后：数据库初始库存：{},redis新库存：{}",
                    stockUpdateType.getMsg(),
                    oldInitStock,
                    StrUtil.isBlank(oldRedisStockStr) ? null : oldRedisStockStr,
                    newInitStock,
                    newRedisStock
            );
        }
        //仅在增加库存时触发：库存不足时用户可加入候补订阅队列，补货后需将新增库存优先分配给候补用户
        if (stockUpdateType == StockUpdateType.INCREASE) {
            //异步执行，避免耗时的资格分配阻塞当前库存更新主流程
            SECKILL_ORDER_EXECUTOR.execute(() -> voucherOrderService
                    //按订阅先后顺序（FIFO）取出最早订阅且尚未购得的用户，将购买资格分配给他
                    //第二个参数 excludeUserId 传 null 表示不排除任何用户，即对队列中的候补用户逐一分配
                    .autoIssueVoucherToEarliestSubscriber(seckillVoucher.getVoucherId(), null));
        }
    }

    @Override
    public void subscribe(VoucherSubscribeDto voucherSubscribeDto) {
        Long voucherId = voucherSubscribeDto.getVoucherId();
        Long userId = UserHolder.getUser().getId();
        String userIdStr = String.valueOf(userId);

        Long ttlSeconds = redisCache.getExpire(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, voucherId),
                TimeUnit.SECONDS);
        if (Objects.isNull(ttlSeconds) || ttlSeconds <= 0) {
            SeckillVoucher sv = seckillVoucherService.lambdaQuery()
                    .eq(SeckillVoucher::getVoucherId, voucherId)
                    .one();
            if (Objects.nonNull(sv) && Objects.nonNull(sv.getEndTime())) {
                ttlSeconds = Math.max(LocalDateTimeUtil.between(LocalDateTimeUtil.now(), sv.getEndTime()).getSeconds(),
                        1L);
            } else {
                ttlSeconds = 3600L;
            }
        }

        //判断用户是否已购
        boolean purchased = Boolean.TRUE.equals(redisCache.isMemberForSet(
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_USER_TAG_KEY, voucherId),
                userIdStr
        ));

        RedisKeyBuild statusKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_STATUS_TAG_KEY, voucherId);
        if (purchased) {
            redisCache.putHash(statusKey, userIdStr, SubscribeStatus.SUCCESS.getCode(), ttlSeconds, TimeUnit.SECONDS);
            redisCache.expire(statusKey, ttlSeconds, TimeUnit.SECONDS);
            return;
        }

        //加入订阅集合
        RedisKeyBuild setKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_USER_TAG_KEY, voucherId);
        Long added = redisCache.addForSet(setKey, userIdStr);
        redisCache.expire(setKey, ttlSeconds, TimeUnit.SECONDS);

        //加入订阅队列（ZSET），仅首次加入时写入顺序分数
        RedisKeyBuild zsetKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_ZSET_TAG_KEY, voucherId);
        if (Objects.nonNull(added) && added > 0) {
            redisCache.addForSortedSet(zsetKey, userIdStr, (double) System.currentTimeMillis(), ttlSeconds, TimeUnit.SECONDS);
        } else {
            redisCache.expire(zsetKey, ttlSeconds, TimeUnit.SECONDS);
        }
        //更新订阅状态为 SUBSCRIBED（如已是 SUCCESS 则不降级）
        Integer prev = redisCache.getForHash(statusKey, userIdStr, Integer.class);
        if (!SubscribeStatus.SUCCESS.getCode().equals(prev)) {
            redisCache.putHash(statusKey, userIdStr, SubscribeStatus.SUBSCRIBED.getCode(), ttlSeconds, TimeUnit.SECONDS);
        }
        redisCache.expire(statusKey, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void unsubscribe(VoucherSubscribeDto voucherSubscribeDto) {
        Long voucherId = voucherSubscribeDto.getVoucherId();
        Long userId = UserHolder.getUser().getId();
        String userIdStr = String.valueOf(userId);

        RedisKeyBuild setKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_USER_TAG_KEY, voucherId);
        RedisKeyBuild statusKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_STATUS_TAG_KEY, voucherId);
        RedisKeyBuild zsetKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_ZSET_TAG_KEY, voucherId);

        //从订阅集合与队列移除
        redisCache.removeForSet(setKey, userIdStr);
        redisCache.delForSortedSet(zsetKey, userIdStr);

        boolean purchased = Boolean.TRUE.equals(redisCache.isMemberForSet(
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_USER_TAG_KEY, voucherId),
                userIdStr
        ));
        Long ttlSeconds = redisCache.getExpire(
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, voucherId),
                TimeUnit.SECONDS
        );
        if (ttlSeconds == null || ttlSeconds <= 0) {
            ttlSeconds = 3600L;
        }
        //已购则维持 SUCCESS，否则置为 UNSUBSCRIBED
        redisCache.putHash(
                statusKey,
                userIdStr,
                purchased ? SubscribeStatus.SUCCESS.getCode() : SubscribeStatus.UNSUBSCRIBED.getCode(),
                ttlSeconds,
                TimeUnit.SECONDS
        );
        redisCache.expire(statusKey, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public Integer getSubscribeStatus(VoucherSubscribeDto voucherSubscribeDto) {
        Long voucherId = voucherSubscribeDto.getVoucherId();
        Long userId = UserHolder.getUser().getId();
        String userIdStr = String.valueOf(userId);
        RedisKeyBuild statusKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_STATUS_TAG_KEY, voucherId);
        Integer st = redisCache.getForHash(statusKey, userIdStr, Integer.class);
        if (st != null) {
            return st;
        }
        Boolean purchased = redisCache.isMemberForSet(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_USER_TAG_KEY, voucherId), userIdStr);
        if (purchased) {
            Long ttlSeconds = redisCache.getExpire(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, voucherId));
            if (ttlSeconds == null || ttlSeconds <= 0) {
                ttlSeconds = 3600L;
            }
            redisCache.putHash(statusKey, userIdStr, SubscribeStatus.SUCCESS.getCode(), ttlSeconds, TimeUnit.SECONDS);
            return SubscribeStatus.SUCCESS.getCode();
        }
        Boolean result = redisCache.isMemberForSet(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_USER_TAG_KEY, voucherId), userIdStr);
        return result ? SubscribeStatus.SUBSCRIBED.getCode() : SubscribeStatus.UNSUBSCRIBED.getCode();
    }

    @Override
    public List<GetSubscribeStatusVo> getSubscribeStatusBatch(VoucherSubscribeBatchDto voucherSubscribeBatchDto) {
        Long userId = UserHolder.getUser().getId();
        String userIdStr = String.valueOf(userId);
        List<GetSubscribeStatusVo> list = new ArrayList<>();
        for (Long voucherId : voucherSubscribeBatchDto.getVoucherIdList()) {
            RedisKeyBuild statusKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_STATUS_TAG_KEY, voucherId);
            Integer st = redisCache.getForHash(statusKey, userIdStr, Integer.class);
            if (st != null) {
                list.add(new GetSubscribeStatusVo(voucherId, st));
                continue;
            }
            Boolean purchased = redisCache.isMemberForSet(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_USER_TAG_KEY, voucherId), userIdStr);
            if (purchased) {
                Long ttlSeconds = redisCache.getExpire(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, voucherId));
                if (ttlSeconds == null || ttlSeconds <= 0) {
                    ttlSeconds = 3600L;
                }
                redisCache.putHash(statusKey, userIdStr, SubscribeStatus.SUCCESS.getCode(), ttlSeconds, TimeUnit.SECONDS);
                list.add(new GetSubscribeStatusVo(voucherId, SubscribeStatus.SUCCESS.getCode()));
                continue;
            }
            Boolean result = redisCache.isMemberForSet(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_USER_TAG_KEY, voucherId), userIdStr);
            list.add(new GetSubscribeStatusVo(voucherId, result ? SubscribeStatus.SUBSCRIBED.getCode() : SubscribeStatus.UNSUBSCRIBED.getCode()));
        }
        return list;
    }


    @Override
    public void delayVoucherReminder(DelayVoucherReminderDto delayVoucherReminderDto) {

    }
}
