package com.junzhecai.cache;

import cn.hutool.core.date.LocalDateTimeUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.junzhecai.model.SeckillVoucherFullModel;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class SeckillVoucherLocalCache {
    /**
     * 使用Caffeine创建本地缓存，键为券ID，值为秒杀券完整信息
     *
     */
    private final Cache<String, SeckillVoucherFullModel> cache = Caffeine.newBuilder()
            // 限制缓存最大容量，防止数据无限增长导致内存溢出
            .maximumSize(10000)
            // 设置自定义过期策略，实现按券结束时间自动失效
            .expireAfter(new Expiry<String, SeckillVoucherFullModel>() {
                // 定义缓存项创建时的过期时间计算逻辑
                @Override
                public long expireAfterCreate(@NonNull String key, @NonNull SeckillVoucherFullModel value, long currentTime) {
                    // 默认设置缓存有效期为60秒，作为兜底策略
                    long ttlSeconds = 60L;
                    // 如果缓存对象存在并包含结束时间，则按结束时间计算剩余秒数
                    if (value.getEndTime() != null) {
                        // 计算当前时间与结束时间之间的秒数，并保证至少保留1秒
                        ttlSeconds = Math.max(
                                LocalDateTimeUtil.between(LocalDateTimeUtil.now(), value.getEndTime()).getSeconds(),
                                1L
                        );
                    }
                    // 将秒转换为纳秒，符合Caffeine过期接口的返回值要求
                    return TimeUnit.NANOSECONDS.convert(ttlSeconds, TimeUnit.SECONDS);
                }

                // 定义缓存项更新后的过期策略
                @Override
                public long expireAfterUpdate(String key, SeckillVoucherFullModel value, long currentTime, long currentDuration) {
                    // 更新时保持原有剩余时间，避免频繁刷新导致长期驻留
                    return currentDuration;
                }

                // 定义缓存项被读取后的过期策略
                @Override
                public long expireAfterRead(String key, SeckillVoucherFullModel value, long currentTime, long currentDuration) {
                    // 读取不改变剩余时间，防止热点数据一直存活
                    return currentDuration;
                }
            })
            // 构建缓存实例，应用上述全部配置
            .build();

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
        // 调用失效方法清除缓存项，保证数据及时更新
        cache.invalidate(voucherId);
    }
}