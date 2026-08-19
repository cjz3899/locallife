package com.junzhecai.core;

import com.junzhecai.servicelock.LockType;
import com.junzhecai.servicelock.ServiceLocker;
import com.junzhecai.servicelock.impl.RedissonFairLocker;
import com.junzhecai.servicelock.impl.RedissonReadLocker;
import com.junzhecai.servicelock.impl.RedissonReentrantLocker;
import com.junzhecai.servicelock.impl.RedissonWriteLocker;
import org.redisson.api.RedissonClient;

import java.util.HashMap;
import java.util.Map;

import static com.junzhecai.servicelock.LockType.*;

public class ManageLocker {

    private final Map<LockType, ServiceLocker> cacheLocker = new HashMap<>();

    public ManageLocker(RedissonClient redissonClient) {
        cacheLocker.put(Reentrant, new RedissonReentrantLocker(redissonClient));
        cacheLocker.put(Fair, new RedissonFairLocker(redissonClient));
        cacheLocker.put(Write, new RedissonWriteLocker(redissonClient));
        cacheLocker.put(Read, new RedissonReadLocker(redissonClient));
    }

    public ServiceLocker getReentrantLocker() {
        return cacheLocker.get(Reentrant);
    }

    public ServiceLocker getFairLocker() {
        return cacheLocker.get(Fair);
    }

    public ServiceLocker getWriteLocker() {
        return cacheLocker.get(Write);
    }

    public ServiceLocker getReadLocker() {
        return cacheLocker.get(Read);
    }
}
