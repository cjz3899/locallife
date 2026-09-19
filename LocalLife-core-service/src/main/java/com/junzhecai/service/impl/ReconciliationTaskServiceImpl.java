package com.junzhecai.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import com.junzhecai.core.RedisKeyManage;
import com.junzhecai.entity.SeckillVoucher;
import com.junzhecai.entity.VoucherOrder;
import com.junzhecai.entity.VoucherReconcileLog;
import com.junzhecai.enums.LogType;
import com.junzhecai.enums.ReconciliationStatus;
import com.junzhecai.model.RedisTraceLogModel;
import com.junzhecai.redis.RedisCache;
import com.junzhecai.redis.RedisKeyBuild;
import com.junzhecai.service.IReconciliationTaskService;
import com.junzhecai.service.ISeckillVoucherService;
import com.junzhecai.service.IVoucherOrderService;
import com.junzhecai.service.IVoucherReconcileLogService;
import com.junzhecai.servicelock.LockType;
import com.junzhecai.servicelock.annotion.ServiceLock;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.junzhecai.constant.DistributedLockConstants.UPDATE_SECKILL_VOUCHER_STOCK_LOCK;
import static com.junzhecai.kafka.consumer.SeckillVoucherConsumer.MESSAGE_DELAY_TIME;

/**
 * 秒杀券库存对账服务
 * 比对 Redis 扣减流水与 DB 订单/对账日志，发现并补偿数据不一致
 */
@Service
@Slf4j
public class ReconciliationTaskServiceImpl implements IReconciliationTaskService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private IVoucherReconcileLogService voucherReconcileLogService;

    @Resource
    private RedisCache redisCache;

    @Override
    public void reconciliationTaskExecute() {
        List<SeckillVoucher> seckillVoucherList = seckillVoucherService.lambdaQuery().list();
        for (SeckillVoucher seckillVoucher : seckillVoucherList) {
            reconciliationTaskExecute(seckillVoucher.getVoucherId());
        }
    }

    /**
     * 对单个券执行对账主流程
     */
    public void reconciliationTaskExecute(Long voucherId) {
        Map<String, RedisTraceLogModel> redisTraceLogMap = loadRedisTraceLogMap(voucherId);
        //先检查执行“Redis扣减流水存在，但DB订单不存在”的逻辑
        redisDeductTraceWithoutDbOrder(voucherId, redisTraceLogMap);

        List<VoucherOrder> voucherOrderList = loadPendingOrders(voucherId);
        RedisKeyBuild traceLogKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_TRACE_LOG_TAG_KEY, voucherId);
        long ttlSeconds = resolveTraceTtlSeconds(traceLogKey, voucherId);
        for (VoucherOrder voucherOrder : voucherOrderList) {
            List<VoucherReconcileLog> logs = loadReconcileLogs(voucherOrder.getId());
            // DB 无对账日志，直接标记异常
            if (CollectionUtil.isEmpty(logs)) {
                ((ReconciliationTaskServiceImpl) AopContext.currentProxy())
                        .markOrderStatus(voucherOrder.getId(), ReconciliationStatus.ABNORMAL);
                continue;
            }
            boolean anyMissing = backfillMissingTraceLogs(logs, redisTraceLogMap, traceLogKey, ttlSeconds);

            // 扣减 + 可选恢复，正常应为 1~2 条
            int dbLogCount = logs.size();
            boolean markConsistent = true;
            if (dbLogCount == 1 || dbLogCount == 2) {
                // 曾回填说明 Redis 与 DB 不同步，删除 Redis 库存触发下次重新同步
                if (anyMissing) {
                    ((IReconciliationTaskService) AopContext.currentProxy()).delRedisStock(voucherId);
                }
            } else {
                // 日志次数异常
                ((ReconciliationTaskServiceImpl) AopContext.currentProxy())
                        .markOrderStatus(voucherOrder.getId(), ReconciliationStatus.ABNORMAL);
                markConsistent = false;
            }
            if (markConsistent) {
                ((ReconciliationTaskServiceImpl) AopContext.currentProxy())
                        .markOrderStatus(voucherOrder.getId(), ReconciliationStatus.CONSISTENT);
            }
        }
    }

    /**
     * 加分布式锁删除 Redis 库存，触发下次重新同步
     */
    @Override
    @ServiceLock(lockType = LockType.Write, name = UPDATE_SECKILL_VOUCHER_STOCK_LOCK, keys = {"#voucherId"})
    public void delRedisStock(Long voucherId) {
        RedisKeyBuild stockKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_STOCK_TAG_KEY, voucherId);
        redisCache.del(stockKey);
    }

    /**
     * 查询 2 分钟前创建、状态仍为待处理的订单
     */
    private List<VoucherOrder> loadPendingOrders(Long voucherId) {
        return voucherOrderService.lambdaQuery()
                .eq(VoucherOrder::getVoucherId, voucherId)
                .le(VoucherOrder::getCreateTime, LocalDateTimeUtil.offset(LocalDateTimeUtil.now(), 2, ChronoUnit.MINUTES))
                .eq(VoucherOrder::getReconciliationStatus, ReconciliationStatus.PENDING.getCode())
                .orderByAsc(VoucherOrder::getCreateTime)
                .list();
    }

    /**
     * 查询指定订单的对账日志
     */
    private List<VoucherReconcileLog> loadReconcileLogs(Long orderId) {
        return voucherReconcileLogService.lambdaQuery()
                .eq(VoucherReconcileLog::getOrderId, orderId)
                .orderByAsc(VoucherReconcileLog::getCreateTime)
                .list();
    }

    /**
     * 读取 Redis 中的扣减流水
     */
    private Map<String, RedisTraceLogModel> loadRedisTraceLogMap(Long voucherId) {
        return redisCache.getAllMapForHash(
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_TRACE_LOG_TAG_KEY, voucherId),
                RedisTraceLogModel.class
        );
    }

    /**
     * 获取流水 Hash 的 TTL，取不到则按券结束时间 + 1 天计算
     */
    private long resolveTraceTtlSeconds(RedisKeyBuild traceLogKey, Long voucherId) {
        Long ttlSeconds = redisCache.getExpire(traceLogKey, TimeUnit.SECONDS);
        if (ttlSeconds != null && ttlSeconds > 0) {
            return ttlSeconds;
        }
        SeckillVoucher voucher = seckillVoucherService.lambdaQuery()
                .eq(SeckillVoucher::getVoucherId, voucherId)
                .one();
        long computedTtl = 3600L;
        if (voucher != null && voucher.getEndTime() != null) {
            LocalDateTime now = LocalDateTimeUtil.now();
            long secondsUntilEnd = Math.max(0L, Duration.between(now, voucher.getEndTime()).getSeconds());
            computedTtl = Math.max(1L, secondsUntilEnd + Duration.ofDays(1).getSeconds());
        }
        return computedTtl;
    }


    /**
     * 处理“Redis 已记录扣减流水，但数据库未落对应扣减流水”的少卖场景
     * 背景：
     * 1. Redis 扣减流水（RedisTraceLogModel）与数据库 tb_voucher_reconcile_log 通过 traceId 一一对应；
     * 2. 当生产者异步发送后进程异常退出，可能出现 Redis 已扣减、Kafka 消息未完成落盘/回调未执行的窗口；
     * 3. 最终表现为：Redis 中看起来库存已减少，但 DB 中没有这笔订单扣减流水。
     * 处理策略：
     * 1. 仅扫描 Redis 中 logType=DEDUCT 的流水；
     * 2. 用 orderId + traceId 到 tb_voucher_reconcile_log 做精确匹配；
     * 3. 若 DB 缺失该流水，则先做“延迟保护窗口”判断，避免把正常的消费延迟误判成异常；
     * 4. 超过保护窗口仍缺失，判定为异常：删除 Redis 库存（每个 voucher 只删一次）并清理该条 Redis 流水
     */
    private void redisDeductTraceWithoutDbOrder(Long voucherId, Map<String, RedisTraceLogModel> redisTraceLogMap) {
        if (voucherId == null || CollectionUtil.isEmpty(redisTraceLogMap)) {
            return;
        }
        RedisKeyBuild traceLogKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_TRACE_LOG_TAG_KEY, voucherId);
        RedisKeyBuild seckillUserKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_USER_TAG_KEY, voucherId);
        boolean delRedisStockHasHappened = false;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, RedisTraceLogModel> redisTraceLogModelEntry : redisTraceLogMap.entrySet()) {
            String traceId = redisTraceLogModelEntry.getKey();
            RedisTraceLogModel redisTraceLogModel = redisTraceLogModelEntry.getValue();
            if (redisTraceLogModel == null) {
                continue;
            }
            // 只处理扣减类型的流水
            if (!String.valueOf(LogType.DEDUCT.getCode()).equals(redisTraceLogModel.getLogType())) {
                continue;
            }
            String orderId = redisTraceLogModel.getOrderId();
            VoucherReconcileLog voucherReconcileLog =
                    voucherReconcileLogService.lambdaQuery()
                            .eq(VoucherReconcileLog::getOrderId, Long.parseLong(orderId))
                            .eq(VoucherReconcileLog::getTraceId, Long.parseLong(traceId))
                            .one();
            if (Objects.isNull(voucherReconcileLog)) {
                Long traceTs = redisTraceLogModel.getTs();
                // 流水仍在消息延迟窗口内，视为消息在途，跳过避免误判
                if (traceTs != null && now - traceTs < (MESSAGE_DELAY_TIME + 2000)) {
                    continue;
                }
                log.error("发现Redis扣减流水存在，但DB订单不存在的情况，voucherId={}, orderId={}", voucherId, orderId);
                // 仅首次触发删除库存，避免重复加锁
                if (!delRedisStockHasHappened) {
                    ((IReconciliationTaskService) AopContext.currentProxy()).delRedisStock(voucherId);
                    delRedisStockHasHappened = true;
                }
                // 清理脏流水并移除用户占用
                redisCache.delForHash(traceLogKey, traceId);
                redisCache.removeForSet(seckillUserKey, redisTraceLogModel.getUserId());
            }
        }
    }

    /**
     * 将 DB 中存在但 Redis 缺失的流水回填到 Redis
     *
     * @return 是否存在缺失并回填
     */
    private boolean backfillMissingTraceLogs(List<VoucherReconcileLog> logs,
                                             Map<String, RedisTraceLogModel> redisTraceLogMap,
                                             RedisKeyBuild traceLogKey,
                                             long ttlSeconds) {
        boolean anyMissing = false;
        for (VoucherReconcileLog log : logs) {
            String traceIdStr = String.valueOf(log.getTraceId());
            RedisTraceLogModel existed = redisTraceLogMap.get(traceIdStr);
            if (existed != null) {
                continue;
            }
            anyMissing = true;
            RedisTraceLogModel model = RedisTraceLogModel.builder()
                    .logType(String.valueOf(log.getLogType()))
                    .ts(LocalDateTimeUtil.toEpochMilli(log.getCreateTime()))
                    .orderId(String.valueOf(log.getOrderId()))
                    .traceId(String.valueOf(log.getTraceId()))
                    .userId(String.valueOf(log.getUserId()))
                    .voucherId(String.valueOf(log.getVoucherId()))
                    .beforeQty(log.getBeforeQty())
                    .changeQty(log.getChangeQty())
                    .afterQty(log.getAfterQty())
                    .build();
            redisCache.putHash(traceLogKey, traceIdStr, model);
            // TTL 已失效时恢复过期时间
            Long currentTtl = redisCache.getExpire(traceLogKey, TimeUnit.SECONDS);
            if (currentTtl == null || currentTtl <= 0) {
                redisCache.expire(traceLogKey, ttlSeconds, TimeUnit.SECONDS);
            }
        }
        return anyMissing;
    }

    /**
     * 事务内同时更新订单与对账日志的状态
     * 涉及到两张表的更新操作，需要添加事务
     */
    @Transactional(rollbackFor = Exception.class)
    public void markOrderStatus(Long orderId, ReconciliationStatus status) {
        voucherOrderService.lambdaUpdate()
                .set(VoucherOrder::getReconciliationStatus, status.getCode())
                .set(VoucherOrder::getUpdateTime, LocalDateTime.now())
                .eq(VoucherOrder::getId, orderId)
                .update();
        voucherReconcileLogService.lambdaUpdate()
                .set(VoucherReconcileLog::getReconciliationStatus, status.getCode())
                .set(VoucherReconcileLog::getUpdateTime, LocalDateTime.now())
                .eq(VoucherReconcileLog::getOrderId, orderId)
                .update();
    }
}
