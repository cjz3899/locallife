package com.junzhecai.service.impl;

import cn.hutool.core.util.IdUtil;
import com.junzhecai.core.RedisKeyManage;
import com.junzhecai.lua.SeckillAccessTokenOperate;
import com.junzhecai.redis.RedisCache;
import com.junzhecai.redis.RedisKeyBuild;
import com.junzhecai.service.ISeckillAccessTokenService;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SeckillAccessTokenServiceImpl implements ISeckillAccessTokenService {
    @Value("${seckill.access.token.enabled:true}")
    private boolean enabled;

    @Value("${seckill.access.token.ttl-seconds:30}")
    private long ttlSeconds;

    @Resource
    private RedisCache redisCache;

    @Resource
    private MeterRegistry meterRegistry;

    @Resource
    private SeckillAccessTokenOperate seckillAccessTokenOperate;

    @Override

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String issueAccessToken(Long voucherId, Long userId) {
        String token = IdUtil.simpleUUID();
        boolean ok = redisCache.setIfAbsent(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_ACCESS_TOKEN_TAG_KEY, voucherId, userId), token, ttlSeconds, TimeUnit.SECONDS);
        if (!ok) {
            String existing = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_ACCESS_TOKEN_TAG_KEY, voucherId, userId),
                    String.class);
            sageInc("seckill_access_token_issue_conflict");
            return existing != null ? existing : token;
        }
        sageInc("seckill_access_token_issue_success");
        log.info("获取令牌成功 令牌：{}", token);
        return token;
    }

    private void sageInc(String name) {
        try {
            if (Objects.nonNull(meterRegistry)) {
                meterRegistry.counter(name, "component", "service_impl").increment();
            }
        } catch (Exception ignore) {
        }
    }

    @Override
    public boolean validateAndConsume(Long voucherId, Long userId, String token) {
        String key = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_ACCESS_TOKEN_TAG_KEY, voucherId, userId).getRelKey();
        boolean success = seckillAccessTokenOperate.validateAndConsume(key, token);
        if (success) {
            sageInc("seckill_access_token_consume_success");
        } else {
            sageInc("seckill_access_token_consume_fail");
        }
        return success;
    }
}
