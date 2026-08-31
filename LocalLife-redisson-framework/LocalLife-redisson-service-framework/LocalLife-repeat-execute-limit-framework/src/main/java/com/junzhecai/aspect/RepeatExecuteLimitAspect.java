package com.junzhecai.aspect;

import com.junzhecai.annotion.RepeatExecuteLimit;
import com.junzhecai.constant.LockInfoType;
import com.junzhecai.exception.LocalLifeFrameException;
import com.junzhecai.handler.RedissonDataHandle;
import com.junzhecai.locallock.LocalLockCache;
import com.junzhecai.lockinfo.LockInfoHandle;
import com.junzhecai.lockinfo.factory.LockInfoHandleFactory;
import com.junzhecai.servicelock.LockType;
import com.junzhecai.servicelock.ServiceLocker;
import com.junzhecai.servicelock.factory.ServiceLockFactory;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static com.junzhecai.constant.RepeatExecuteLimitConstant.PREFIX_NAME;
import static com.junzhecai.constant.RepeatExecuteLimitConstant.SUCCESS_FLAG;

@Slf4j
@Aspect
@Order(-11)
@AllArgsConstructor
public class RepeatExecuteLimitAspect {
    private LockInfoHandleFactory lockInfoHandleFactory;
    private RedissonDataHandle redissonDataHandle;
    private LocalLockCache localLockCache;
    private ServiceLockFactory serviceLockFactory;


    //本地锁先拦截住同一订单号的多余请求，再通过分布式锁保持同一时间只有一个请求在执行（分布式架构中，存在多台服务器）
    @Around("@annotation(repeatExecuteLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RepeatExecuteLimit repeatExecuteLimit) throws Throwable {
        //指定保持幂等的时间
        long durationTime = repeatExecuteLimit.durationTime();
        //提示信息
        String message = repeatExecuteLimit.message();
        Object obj;
        //获取锁信息
        LockInfoHandle lockInfoHandle = lockInfoHandleFactory.getLockInfoHandle(LockInfoType.REPEAT_EXECUTE_LIMIT);
        //解析锁名字
        String lockName = lockInfoHandle.getLockName(joinPoint, repeatExecuteLimit.name(), repeatExecuteLimit.keys());
        //幂等标识
        String repeatFlgName = PREFIX_NAME + lockName;
        //获取幂等标识
        String flagObject = redissonDataHandle.get(repeatFlgName);
        //若幂等标识的值为success，说明已经有请求在执行了，直接结束
        if (SUCCESS_FLAG.equals(flagObject)) {
            throw new LocalLifeFrameException(message);
        }
        //获取本地锁，第二个参数表示是否公平锁，false表示不公平锁
        ReentrantLock localLock = localLockCache.getLock(lockName, false);
        boolean localLockResult = localLock.tryLock();
        if (!localLockResult) {
            throw new LocalLifeFrameException(message);
        }
        try {
            //获取分布式锁
            ServiceLocker lock = serviceLockFactory.getLock(LockType.Reentrant);
            boolean result = lock.tryLock(lockName, TimeUnit.SECONDS, 0);
            if (result) {
                try {
                    //再次获取幂等标识
                    flagObject = redissonDataHandle.get(repeatFlgName);
                    if (SUCCESS_FLAG.equals(flagObject)) {
                        throw new LocalLifeFrameException(message);
                    }
                    //执行业务
                    obj = joinPoint.proceed();
                    if (durationTime > 0) {
                        try {
                            redissonDataHandle.set(repeatFlgName, SUCCESS_FLAG, durationTime, TimeUnit.SECONDS);
                        } catch (Exception e) {
                            log.error("getBucket error", e);
                        }
                    }
                    return obj;
                } finally {
                    lock.unlock(lockName);
                }
            } else {
                throw new LocalLifeFrameException(message);
            }
        } finally {
            localLock.unlock();
        }
    }
}
