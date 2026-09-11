package com.junzhecai.config;

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
    public RedisRateLimitHandler rateLimitHandler(SeckillRateLimitConfigProperties seckillRateLimitConfigProperties,
                                                  RedisCache redisCache,
                                                  RateLimitSlidingOperate rateLimitSlidingOperate,
                                                  RateLimitTokenBucketOperate rateLimitTokenBucketOperate,
                                                  ) {

    }

    @Bean
    public SeckillAccessTokenOperate accessTokenOperate(RedisCache redisCache) {
        return new SeckillAccessTokenOperate(redisCache);
    }
}
