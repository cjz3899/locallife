package com.junzhecai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import com.junzhecai.dto.CancelVoucherOrderDto;
import com.junzhecai.dto.GetVoucherOrderByVoucherIdDto;
import com.junzhecai.dto.GetVoucherOrderDto;
import com.junzhecai.dto.Result;
import com.junzhecai.entity.VoucherOrder;
import com.junzhecai.mapper.VoucherOrderMapper;
import com.junzhecai.service.IVoucherOrderService;
import org.springframework.stereotype.Service;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    

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

    private static class NamedThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final boolean daemon;
        private final AtomicInteger index = new AtomicInteger(1);

        public NamedThreadFactory(String namePrefix, boolean daemon) {
            this.namePrefix = namePrefix;
            this.daemon = daemon;
        }

        @Override
        public Thread newThread(Runnable r) {
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
        return null;
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
