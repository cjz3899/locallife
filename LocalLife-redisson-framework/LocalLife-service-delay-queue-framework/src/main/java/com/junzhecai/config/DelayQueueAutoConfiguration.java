package com.junzhecai.config;

import com.junzhecai.context.DelayQueueBasePart;
import com.junzhecai.context.DelayQueueContext;
import org.redisson.api.RedissonClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@EnableConfigurationProperties(DelayQueueProperties.class)
public class DelayQueueAutoConfiguration {
    @Bean
    public DelayQueueBasePart delayQueueBasePart(RedissonClient redissonClient, DelayQueueProperties delayQueueProperties) {
        return new DelayQueueBasePart(redissonClient, delayQueueProperties);
    }

    @Bean
    public DelayQueueContext delayQueueContext(DelayQueueBasePart delayQueueBasePart) {
        return new DelayQueueContext(delayQueueBasePart);
    }
}
