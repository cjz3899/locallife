package com.junzhecai.cache;

import com.junzhecai.core.RedisKeyManage;
import com.junzhecai.core.SpringUtil;
import com.junzhecai.kafka.message.SeckillVoucherInvalidationMessage;
import com.junzhecai.kafka.producer.SeckillVoucherInvalidationProducer;
import com.junzhecai.redis.RedisCache;
import com.junzhecai.redis.RedisKeyBuild;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

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

    public void publishInvalidate(Long voucherId, String reason) {
        //清理redis缓存
        RedisKeyBuild seckillVoucherRedisKey =
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, voucherId);
        seckillVoucherLocalCache.invalidate(seckillVoucherRedisKey.getRelKey());
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
