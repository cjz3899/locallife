package com.junzhecai.cache;

import com.junzhecai.core.RedisKeyManage;
import com.junzhecai.core.SpringUtil;
import com.junzhecai.kafka.message.SeckillVoucherInvalidationMessage;
import com.junzhecai.kafka.producer.SeckillVoucherInvalidationProducer;
import com.junzhecai.redis.RedisCache;
import com.junzhecai.redis.RedisKeyBuild;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static com.junzhecai.constant.Constant.SECKILL_VOUCHER_CACHE_INVALIDATION_TOPIC;

//业务发布入口：触发秒杀券缓存失效广播
@Component
public class SeckillVoucherCacheInvalidationPublisher {

    @Resource
    private RedisCache redisCache;

    @Resource
    private SeckillVoucherInvalidationProducer invalidationProducer;

    @Resource
    private SeckillVoucherLocalCache seckillVoucherLocalCache;

    /**
     * 在当前事务提交之后再失效缓存
     *
     * 必须在事务提交后执行：如果事务尚未提交就删缓存，另一个读请求会在这个空窗期回源数据库，
     * 读到的是未提交的旧值并写回缓存，事务提交后缓存里就留下了脏数据，直到TTL到期才会自愈。
     *
     */
    public void publishInvalidateAfterCommit(Long voucherId, String reason) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishInvalidate(voucherId, reason);
                }
            });
        } else {
            publishInvalidate(voucherId, reason);
        }
    }

    public void publishInvalidate(Long voucherId, String reason) {
        //清理redis缓存
        RedisKeyBuild seckillVoucherRedisKey =
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, voucherId);
        //本地缓存需要同时清掉券主体和空值哨兵，保证与下面Redis侧的清理范围一致
        seckillVoucherLocalCache.invalidateVoucher(voucherId);
        redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, voucherId));
        redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_STOCK_TAG_KEY, voucherId));
        redisCache.del(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_NULL_TAG_KEY, voucherId));

        //广播消息到所有实例
        SeckillVoucherInvalidationMessage payload = new SeckillVoucherInvalidationMessage(voucherId, reason);
        invalidationProducer.sendPayload(
                SpringUtil.getPrefixDistinctionName() + "-" + SECKILL_VOUCHER_CACHE_INVALIDATION_TOPIC,
                payload
        );
    }
}
