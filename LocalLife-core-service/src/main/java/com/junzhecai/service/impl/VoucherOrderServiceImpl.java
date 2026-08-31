package com.junzhecai.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.dto.CancelVoucherOrderDto;
import com.junzhecai.dto.GetVoucherOrderByVoucherIdDto;
import com.junzhecai.dto.GetVoucherOrderDto;
import com.junzhecai.dto.Result;
import com.junzhecai.entity.SeckillVoucher;
import com.junzhecai.entity.VoucherOrder;
import com.junzhecai.exception.LocalLifeFrameException;
import com.junzhecai.mapper.VoucherOrderMapper;
import com.junzhecai.service.ISeckillVoucherService;
import com.junzhecai.service.IVoucherOrderService;
import com.junzhecai.toolkit.SnowflakeIdGenerator;
import com.junzhecai.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    public static final ThreadPoolExecutor SECKILL_ORDER_EXECUTOR =
            new ThreadPoolExecutor(
                    1,
                    1,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(1024),
                    new NamedThreadFactory("seckill-order-", false),
                    new ThreadPoolExecutor.CallerRunsPolicy()
            );
    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    private static class NamedThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final boolean daemon;
        private final AtomicInteger index = new AtomicInteger(1);

        public NamedThreadFactory(String namePrefix, boolean daemon) {
            this.namePrefix = namePrefix;
            this.daemon = daemon;
        }

        @Override
        public Thread newThread(@NonNull Runnable r) {
            Thread t = new Thread(r, namePrefix + index.getAndIncrement());
            t.setDaemon(daemon);
            t.setUncaughtExceptionHandler((thread, ex) ->
                    log.error("未捕获异常，线程={}, err={}", thread.getName(), ex.getMessage(), ex)
            );
            return t;
        }
    }

    @Override
    public Result<Long> seckillVoucher(Long voucherId) {
        SeckillVoucher seckillVoucher = seckillVoucherService.query().eq("voucher_id", voucherId).one();
        if (seckillVoucher == null) {
            throw new LocalLifeFrameException("优惠券不存在");
        }
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTimeUtil.now())) {
            return Result.fail("秒杀尚未开始");
        }
        if (seckillVoucher.getEndTime().isBefore(LocalDateTimeUtil.now())) {
            return Result.fail("秒杀已结束");
        }
        Long userId = UserHolder.getUser().getId();
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("库存不足");
        }
        //扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("库存不足");
        }
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(snowflakeIdGenerator.nextId());
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setCreateTime(LocalDateTimeUtil.now());
        save(voucherOrder);
        return Result.ok(voucherOrder.getId());
    }

    @Override
    public Long getSeckillVoucherOrder(GetVoucherOrderDto getVoucherOrderDto) {
        return null;
    }

    @Override
    public Long getSeckillVoucherOrderIdByVoucherId(GetVoucherOrderByVoucherIdDto getVoucherOrderByVoucherIdDto) {
        return null;
    }

    @Override
    public Boolean cancel(CancelVoucherOrderDto cancelVoucherOrderDto) {
        return false;
    }

    @Override
    public boolean autoIssueVoucherToEarliestSubscriber(final Long voucherId, final Long excludeUserId) {
        return false;
    }
}
