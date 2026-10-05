package com.junzhecai.extension;

import com.junzhecai.enums.BaseCode;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.LongAdder;

@Slf4j
public class NoOpRateLimitEventListener implements RateLimitEventListener {
    private static final LongAdder BEFORE_EXECUTE_COUNTER = new LongAdder();
    private static final LongAdder ALLOWED_COUNTER = new LongAdder();
    private static final LongAdder BLOCKED_COUNTER = new LongAdder();

    @Override
    public void onBeforeExecute(RateLimitContext ctx) {
        BEFORE_EXECUTE_COUNTER.increment();
        if (log.isDebugEnabled()) {
            log.debug("rate-limit.before: voucherId={}, userId={}, ip={}, useSliding={}, keys={}",
                    ctx.getVoucherId(), ctx.getUserId(), ctx.getClientIp(), ctx.isUserSliding(), ctx.getKeys());
        }
    }

    @Override
    public void onAllowed(RateLimitContext ctx) {
        ALLOWED_COUNTER.increment();
        if (log.isDebugEnabled()) {
            log.debug("rate-limit.allowed: voucherId={}, userId={}, ip={}, result={}",
                    ctx.getVoucherId(), ctx.getUserId(), ctx.getClientIp(), ctx.getResult());
        }
    }

    @Override
    public void onBlocked(RateLimitContext ctx, BaseCode reason) {
        BLOCKED_COUNTER.increment();
        log.warn("rate-limit.blocked: reason={}, voucherId={}, userId={}, ip={}, window(ip={},user={}), attempts(ip={},user={})",
                reason,
                ctx.getVoucherId(), ctx.getUserId(), ctx.getClientIp(),
                ctx.getIpLimitWindowMills(), ctx.getUserLimitWindowMills(),
                ctx.getIpLimitMaxAttempts(), ctx.getUserLimitMaxAttempts());
    }

    public long getBeforeExecuteCount() {
        return BEFORE_EXECUTE_COUNTER.sum();
    }

    public long getAllowedCount() {
        return ALLOWED_COUNTER.sum();
    }

    public long getBlockedCount() {
        return BLOCKED_COUNTER.sum();
    }
}
