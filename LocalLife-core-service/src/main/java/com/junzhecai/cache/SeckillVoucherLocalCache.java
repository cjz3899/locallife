package com.junzhecai.cache;

import cn.hutool.core.date.LocalDateTimeUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.junzhecai.core.RedisKeyManage;
import com.junzhecai.model.SeckillVoucherFullModel;
import com.junzhecai.redis.RedisKeyBuild;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class SeckillVoucherLocalCache {

    /**
     * 无法从券信息中取到结束时间时的兜底有效期（秒）
     */
    private static final long DEFAULT_TTL_SECONDS = 60L;

    /**
     * 活动已结束后的兜底有效期（秒）
     * 已结束的券属于终态数据，若沿用"至少1秒"的写法，其本地缓存会被反复重建，
     * 每次都要抢锁回源，因此给一个固定有效期
     */
    private static final long ENDED_TTL_SECONDS = 300L;

    /**
     * 使用Caffeine创建本地缓存，键为券ID，值为秒杀券完整信息
     *
     */
    private final Cache<String, SeckillVoucherFullModel> cache = Caffeine.newBuilder()
            //限制缓存最大容量，防止数据无限增长导致内存溢出
            .maximumSize(10000)
            //设置自定义过期策略，实现按券结束时间自动失效
            .expireAfter(new Expiry<String, SeckillVoucherFullModel>() {
                //定义缓存项创建时的过期时间计算逻辑
                @Override
                public long expireAfterCreate(@NonNull String key, @NonNull SeckillVoucherFullModel value, long currentTime) {
                    //将秒转换为纳秒，符合Caffeine过期接口的返回值要求
                    return TimeUnit.NANOSECONDS.convert(ttlSeconds(value), TimeUnit.SECONDS);
                }

                //定义缓存项更新后的过期策略
                @Override
                public long expireAfterUpdate(@NonNull String key, @NonNull SeckillVoucherFullModel value, long currentTime, long currentDuration) {
                    // 更新时保持原有剩余时间，避免频繁刷新导致长期驻留
                    return currentDuration;
                }

                //定义缓存项被读取后的过期策略
                @Override
                public long expireAfterRead(@NonNull String key, @NonNull SeckillVoucherFullModel value, long currentTime, long currentDuration) {
                    // 读取不改变剩余时间，防止热点数据一直存活
                    return currentDuration;
                }
            })
            // 构建缓存实例，应用上述全部配置
            .build();

    /**
     * 计算缓存项的有效期（秒）
     *
     * 注意：expireAfterCreate 的 currentTime 参数来自 Caffeine 的 Ticker（默认是 nanoTime 的纳秒值），
     * 与业务使用的墙钟时间不同源，不能用来和券的结束时间相减，因此这里重新取系统时间。
     *
     */
    private long ttlSeconds(SeckillVoucherFullModel value) {
        if (value.getEndTime() == null) {
            return DEFAULT_TTL_SECONDS;
        }
        long remainSeconds = LocalDateTimeUtil.between(LocalDateTimeUtil.now(), value.getEndTime()).getSeconds();
        //活动已结束：进入终态，给固定有效期，避免 TTL 恒为1秒导致反复回源
        return remainSeconds > 0 ? remainSeconds : ENDED_TTL_SECONDS;
    }

    /**
     * 根据券ID从缓存中获取秒杀券详情
     *
     */
    public SeckillVoucherFullModel get(String voucherId) {
        // 仅在缓存中存在数据时返回，避免触发加载逻辑
        return cache.getIfPresent(voucherId);
    }

    /**
     * 将新的秒杀券详情写入缓存
     *
     */
    public void put(String voucherId, SeckillVoucherFullModel voucher) {
        // 校验券ID和券信息均不为空，确保写入数据有效
        if (voucherId != null && voucher != null) {
            // 使用Caffeine提供的put操作保存键值对
            cache.put(voucherId, voucher);
        }
    }

    /**
     * 根据券ID主动移除缓存数据
     *
     */
    public void invalidate(String voucherId) {
        if (voucherId != null) {
            // 调用失效方法清除缓存项，保证数据及时更新
            cache.invalidate(voucherId);
        }
    }

    /**
     * 失效指定券在本地缓存中的**全部**相关数据：券主体 + 空值哨兵
     *
     * 只清券主体会留下一个隐患：空值哨兵存活到 TTL 到期之前，
     * 即使券已经上架并被正确写入Redis与布隆过滤器，本地缓存仍会把请求判定为"券不存在"。
     * 因此本地失效的范围必须与Redis侧保持一致（Redis侧两个key都会删）。
     *
     */
    public void invalidateVoucher(Long voucherId) {
        if (voucherId == null) {
            return;
        }
        invalidate(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, voucherId).getRelKey());
        invalidate(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_NULL_TAG_KEY, voucherId).getRelKey());
    }
}
