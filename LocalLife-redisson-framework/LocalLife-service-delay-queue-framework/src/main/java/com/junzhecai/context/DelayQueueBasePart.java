package com.junzhecai.context;

import com.junzhecai.config.DelayQueueProperties;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.redisson.api.RedissonClient;

@Getter
@AllArgsConstructor
public class DelayQueueBasePart {
    private final RedissonClient redissonClient;
    private final DelayQueueProperties delayQueueProperties;
}

