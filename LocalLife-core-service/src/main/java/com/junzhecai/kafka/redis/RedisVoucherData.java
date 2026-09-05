package com.junzhecai.kafka.redis;

import cn.hutool.core.collection.ListUtil;
import com.junzhecai.core.RedisKeyManage;
import com.junzhecai.entity.RollbackFailureLog;
import com.junzhecai.enums.BaseCode;
import com.junzhecai.enums.LogType;
import com.junzhecai.enums.SeckillVoucherOrderOperate;
import com.junzhecai.lua.SeckillVoucherRollBackOperate;
import com.junzhecai.redis.RedisKeyBuild;
import com.junzhecai.service.IRollbackAlertService;
import com.junzhecai.service.IRollbackFailureLogService;
import com.junzhecai.toolkit.SnowflakeIdGenerator;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

//Redis 秒杀订单回滚数据操作组件
@Slf4j
@Component
public class RedisVoucherData {

    @Resource
    private SeckillVoucherRollBackOperate seckillVoucherRollBackOperate;

    @Resource
    private IRollbackFailureLogService rollbackFailureLogService;

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Resource
    private MeterRegistry meterRegistry;

    @Resource
    private IRollbackAlertService rollbackAlertService;

    //最大重试次数
    @Value("${seckill.rollback.retry.maxAttempts:3}")
    private int retryMaxAttempts;

    //初始重试间隔时间（毫秒）
    @Value("${seckill.rollback.retry.initialBackoffMillis:200}")
    private long initialBackoffMillis;

    //最大重试间隔时间（毫秒）
    @Value("${seckill.rollback.retry.maxBackoffMillis:1000}")
    private long maxBackoffMillis;

    public void rollbackRedisVoucherData(SeckillVoucherOrderOperate seckillVoucherOrderOperate,
                                         Long traceId,
                                         Long voucherId,
                                         Long userId,
                                         Long orderId,
                                         Integer beforeQty,
                                         Integer changeQty,
                                         Integer afterQty) {
        List<String> keys = ListUtil.of(
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_STOCK_TAG_KEY, voucherId).getRelKey(),
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_USER_TAG_KEY, voucherId).getRelKey(),
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_TRACE_LOG_TAG_KEY, voucherId).getRelKey()
        );
        String[] args = new String[9];
        args[0] = String.valueOf(voucherId);
        args[1] = String.valueOf(userId);
        args[2] = String.valueOf(orderId);
        args[3] = String.valueOf(seckillVoucherOrderOperate.getCode());
        args[4] = String.valueOf(traceId);
        args[5] = String.valueOf(LogType.RESTORE.getCode());
        args[6] = String.valueOf(beforeQty);
        args[7] = String.valueOf(changeQty);
        args[8] = String.valueOf(afterQty);

        //
        Integer finalCode = luaRollbackWithResultCode(keys, args, retryMaxAttempts, initialBackoffMillis, maxBackoffMillis);
        boolean ok = BaseCode.SUCCESS.getCode().equals(finalCode);
        if (!ok) {
            String reason = BaseCode.getMsg(finalCode == null ? -1 : finalCode);
            log.error("Redis回滚最终失败|voucherId={}|userId={}|orderId={}|traceId={} reason={}", voucherId, userId, orderId, traceId, reason);
            saveRollbackFailureLog(voucherId, userId, orderId, traceId, "redis rollback failed after retries: " + reason, finalCode);
            safeInc("seckill_rollback_retry_give_up", "component", "redis_voucher_data");
        }
    }

    private Integer luaRollbackWithResultCode(
            List<String> keys,
            String[] args,
            int maxAttempts,
            long initialBackoffMs,
            long maxBackoffMs) {
        int attempt = 0;
        long backoff = Math.max(50, initialBackoffMs);
        Integer lastCode = null;
        while (true) {
            try {
                Integer result = seckillVoucherRollBackOperate.execute(keys, args);
                lastCode = result;
                if (BaseCode.SUCCESS.getCode().equals(result)) {
                    safeInc("seckill_rollback_retry_success", "component", "redis_voucher_data");
                    return result;
                }
                String reason = BaseCode.getMsg(result == null ? -1 : result);
                log.warn("Redis回滚失败，准备重试|attempt={} reason={}", attempt + 1, reason);
            } catch (Exception e) {
                lastCode = -1;
                log.warn("Redis回滚异常，准备重试|attempt={} error={}", attempt + 1, e.getMessage());
            }
            attempt++;
            if (attempt >= maxAttempts) {
                break;
            }
            sleepQuietly(withJitter(backoff));
            backoff = Math.min(backoff * 2, Math.max(backoff, maxBackoffMs));
        }
        return lastCode;
    }

    private long withJitter(long base) {
        long jitter = Math.round(base * 0.15 * Math.random());
        return base + jitter;
    }

    private void sleepQuietly(long backoffMs) {
        try {
            TimeUnit.MILLISECONDS.sleep(backoffMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private void saveRollbackFailureLog(Long voucherId, Long userId, Long orderId, Long traceId, String detail, Integer resultCode) {
        try {
            RollbackFailureLog logEntity = RollbackFailureLog.builder()
                    .id(snowflakeIdGenerator.nextId())
                    .orderId(orderId)
                    .userId(userId)
                    .voucherId(voucherId)
                    .detail(detail)
                    .resultCode(resultCode)
                    .traceId(traceId)
                    .retryAttempts(retryMaxAttempts)
                    .source("redis_voucher_data")
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            rollbackFailureLogService.save(logEntity);
            safeInc("seckill_rollback_failure", "component", "redis_voucher_data");
            safeInc("seckill_rollback_failure", "reason", "retry_exhausted");
            //通知回滚失败
            rollbackAlertService.sendRollbackAlert(logEntity);
        } catch (Exception e) {
            log.warn("保存回滚失败日志异常", e);
        }

    }

    private void safeInc(String name, String tagKey, String tagValue) {
        try {
            if (meterRegistry != null) {
                meterRegistry.counter(name, tagKey, tagValue).increment();
            }
        } catch (Exception ignore) {
        }
    }
}
