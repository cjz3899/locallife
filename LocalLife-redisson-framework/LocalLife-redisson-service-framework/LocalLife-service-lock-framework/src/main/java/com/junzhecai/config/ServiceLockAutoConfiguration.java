package com.junzhecai.config;

import com.junzhecai.constant.LockInfoType;
import com.junzhecai.core.ManageLocker;
import com.junzhecai.lockinfo.LockInfoHandle;
import com.junzhecai.lockinfo.factory.LockInfoHandleFactory;
import com.junzhecai.lockinfo.impl.ServiceLockInfoHandle;
import com.junzhecai.servicelock.aspect.ServiceLockAspect;
import com.junzhecai.servicelock.factory.ServiceLockFactory;
import com.junzhecai.utils.ServiceLockTool;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;

public class ServiceLockAutoConfiguration {

    @Bean(LockInfoType.SERVICE_LOCK)
    public LockInfoHandle serviceLockInfoHandle() {
        return new ServiceLockInfoHandle();
    }

    @Bean
    public ManageLocker manageLocker(RedissonClient redissonClient) {
        return new ManageLocker(redissonClient);
    }

    @Bean
    public ServiceLockFactory serviceLockFactory(ManageLocker manageLocker) {
        return new ServiceLockFactory(manageLocker);
    }

    @Bean
    public ServiceLockAspect serviceLockAspect(LockInfoHandleFactory lockInfoHandleFactory, ServiceLockFactory serviceLockFactory) {
        return new ServiceLockAspect(lockInfoHandleFactory, serviceLockFactory);
    }

    @Bean
    public ServiceLockTool serviceLockTooL(LockInfoHandleFactory lockInfoHandleFactory, ServiceLockFactory serviceLockFactory) {
        return new ServiceLockTool(lockInfoHandleFactory, serviceLockFactory);
    }
}

