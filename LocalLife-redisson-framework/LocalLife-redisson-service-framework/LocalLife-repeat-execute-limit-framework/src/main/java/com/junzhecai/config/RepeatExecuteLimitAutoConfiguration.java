package com.junzhecai.config;

import com.junzhecai.aspect.RepeatExecuteLimitAspect;
import com.junzhecai.constant.LockInfoType;
import com.junzhecai.handler.RedissonDataHandle;
import com.junzhecai.locallock.LocalLockCache;
import com.junzhecai.lockinfo.LockInfoHandle;
import com.junzhecai.lockinfo.factory.LockInfoHandleFactory;
import com.junzhecai.lockinfo.impl.RepeatExecuteLimitLockInfoHandle;
import com.junzhecai.servicelock.factory.ServiceLockFactory;
import org.springframework.context.annotation.Bean;

public class RepeatExecuteLimitAutoConfiguration {
    @Bean(LockInfoType.REPEAT_EXECUTE_LIMIT)
    public LockInfoHandle repeatExecuteLimitHandle() {
        return new RepeatExecuteLimitLockInfoHandle();
    }

    @Bean
    public RepeatExecuteLimitAspect repeatExecuteLimitAspect(LockInfoHandleFactory lockInfoHandleFactory,
                                                             RedissonDataHandle redissonDataHandle,
                                                             LocalLockCache localLockCache,
                                                             ServiceLockFactory serviceLockFactory) {
        return new RepeatExecuteLimitAspect(lockInfoHandleFactory, redissonDataHandle, localLockCache, serviceLockFactory);
    }
}
