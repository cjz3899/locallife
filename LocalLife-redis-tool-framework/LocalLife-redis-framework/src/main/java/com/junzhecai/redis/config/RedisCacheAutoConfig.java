package com.junzhecai.redis.config;

import com.junzhecai.redis.RedisCacheImpl;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

//redis封装实现配置
public class RedisCacheAutoConfig {
    @Bean
    public RedisCacheImpl redisCache(@Qualifier("redisToolStringRedisTemplate") StringRedisTemplate stringRedisTemplate) {
        return new RedisCacheImpl(stringRedisTemplate);
    }
}

