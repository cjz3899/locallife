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
import com.junzhecai.exception.LocalLifeFrameException;
import com.junzhecai.mapper.VoucherMapper;
import com.junzhecai.redis.RedisCache;
import com.junzhecai.redis.RedisKeyBuild;
import com.junzhecai.service.ISeckillVoucherService;
import com.junzhecai.service.IVoucherOrderService;
import com.junzhecai.service.IVoucherService;
import com.junzhecai.servicelock.LockType;
import com.junzhecai.servicelock.annotion.ServiceLock;
import com.junzhecai.vo.GetSubscribeStatusVo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    public void subscribe(final VoucherSubscribeDto voucherSubscribeDto) {

    }

    @Override
    public void unsubscribe(final VoucherSubscribeDto voucherSubscribeDto) {

    }

    @Override
    public Integer getSubscribeStatus(final VoucherSubscribeDto voucherSubscribeDto) {
        return null;
    }

    @Override
    public List<GetSubscribeStatusVo> getSubscribeStatusBatch(final VoucherSubscribeBatchDto voucherSubscribeBatchDto) {
        return null;
    }


    @Override
    public void delayVoucherReminder(DelayVoucherReminderDto delayVoucherReminderDto) {

    }
}
