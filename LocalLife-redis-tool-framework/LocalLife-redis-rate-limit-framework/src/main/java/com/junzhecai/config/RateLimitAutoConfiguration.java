package com.junzhecai.config;

import com.junzhecai.extension.*;
import com.junzhecai.handler.RedisRateLimitHandler;
import com.junzhecai.lua.RateLimitSlidingOperate;
import com.junzhecai.lua.RateLimitTokenBucketOperate;
import com.junzhecai.lua.SeckillAccessTokenOperate;
import com.junzhecai.redis.RedisCache;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@EnableConfigurationProperties(SeckillRateLimitConfigProperties.class)
public class RateLimitAutoConfiguration {

    @Bean
    public SeckillAccessTokenOperate seckillAccessTokenOperate(RedisCache redisCache) {
        return new SeckillAccessTokenOperate(redisCache);
    }

    @Bean
    public RateLimitSlidingOperate rateLimitSlidingOperate(RedisCache redisCache) {
        return new RateLimitSlidingOperate(redisCache);
    }

    @Bean
    public RateLimitTokenBucketOperate rateLimitTokenBucketOperate(RedisCache redisCache) {
        return new RateLimitTokenBucketOperate(redisCache);
    }

    @Bean
    public RateLimitEventListener rateLimitEventListener() {
        return new NoOpRateLimitEventListener();
    }

    @Bean
    public RateLimitPenaltyPolicy rateLimitPenaltyPolicy(SeckillRateLimitConfigProperties seckillRateLimitConfigProperties,
                                                         RedisCache redisCache) {

        Boolean enable = seckillRateLimitConfigProperties.getEnablePenalty();
        if (Boolean.TRUE.equals(enable)) {
            return new ThresholdPenaltyPolicy(redisCache, seckillRateLimitConfigProperties);
        }
        return new NoOpRateLimitPenaltyPolicy();
    }

    @Bean
    public RedisRateLimitHandler redisRateLimitHandler(SeckillRateLimitConfigProperties seckillRateLimitConfigProperties,
                                                       RedisCache redisCache,
                                                       RateLimitSlidingOperate slidingRateLimitOperate,
                                                       RateLimitTokenBucketOperate tokenBucketRateLimitOperate,
                                                       RateLimitEventListener rateLimitEventListener,
                                                       RateLimitPenaltyPolicy rateLimitPenaltyPolicy) {
        return new RedisRateLimitHandler(
                seckillRateLimitConfigProperties,
                redisCache,
                slidingRateLimitOperate,
                tokenBucketRateLimitOperate,
                rateLimitEventListener,
                rateLimitPenaltyPolicy
        );
    }
}
