package com.junzhecai.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

//redis配置
@AutoConfiguration
public class RedisFrameWorkAutoConfig {
    @Bean("redisToolRedisTemplate")
    public RedisTemplate redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate redisTemplate = new RedisTemplate();
        //设置序列化器
        redisTemplate.setDefaultSerializer(new StringRedisSerializer());
        //绑定连接工厂
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        return redisTemplate;
    }

    @Primary//同类型bean，优先注入
    @Bean("redisToolStringRedisTemplate")
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        StringRedisTemplate myStringRedisTemplate = new StringRedisTemplate();
        myStringRedisTemplate.setDefaultSerializer(new StringRedisSerializer());
        myStringRedisTemplate.setConnectionFactory(redisConnectionFactory);
        return myStringRedisTemplate;
    }
}
