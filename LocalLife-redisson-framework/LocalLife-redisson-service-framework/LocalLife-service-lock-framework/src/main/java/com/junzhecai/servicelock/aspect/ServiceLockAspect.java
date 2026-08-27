package com.junzhecai.servicelock.aspect;

import cn.hutool.core.util.StrUtil;
import com.junzhecai.constant.LockInfoType;
import com.junzhecai.lockinfo.LockInfoHandle;
import com.junzhecai.lockinfo.factory.LockInfoHandleFactory;
import com.junzhecai.servicelock.LockType;
import com.junzhecai.servicelock.ServiceLocker;
import com.junzhecai.servicelock.annotion.ServiceLock;
import com.junzhecai.servicelock.factory.ServiceLockFactory;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Order(-10)//锁切面优先级，需要高于事务
@AllArgsConstructor
public class ServiceLockAspect {

    private final LockInfoHandleFactory lockInfoHandleFactory;

    private final ServiceLockFactory serviceLockFactory;


    /*环绕通知
     * 原因：在一个完整业务逻辑中，需要在方法执行前进行锁的获取，在方法执行后进行锁的释放
     * */
    @Around("@annotation(servicelock)")
    public Object around(ProceedingJoinPoint joinPoint, ServiceLock servicelock) throws Throwable {
        //获取锁的名字解析处理器
        LockInfoHandle lockInfoHandle = lockInfoHandleFactory.getLockInfoHandle(LockInfoType.SERVICE_LOCK);
        //解析拼接锁的名字
        String lockName = lockInfoHandle.getLockName(joinPoint, servicelock.name(), servicelock.keys());
        //锁的类型，默认可重入锁
        LockType lockType = servicelock.lockType();
        long waitTime = servicelock.waitTime();
        TimeUnit timeUnit = servicelock.timeUnit();

        ServiceLocker lock = serviceLockFactory.getLock(lockType);
        boolean result = lock.tryLock(lockName, timeUnit, waitTime);

        if (result) {
            try {
                //获取到锁，执行业务方法
                return joinPoint.proceed();
            } finally {
                lock.unlock(lockName);
            }
        } else {
            log.warn("Timeout while acquiring serviceLock:{}", lockName);
            String customLockTimeoutStrategy = servicelock.customLockTimeoutStrategy();
            if (StrUtil.isNotEmpty(customLockTimeoutStrategy)) {
                //没获取到锁，执行自定义的锁超时策略
                return handleCustomLockTimeoutStrategy(customLockTimeoutStrategy, joinPoint);
            } else {
                //没获取到锁，执行默认的锁超时策略，即快速失效抛出异常
                servicelock.lockTimeoutStrategy().handler(lockName);
            }
            return joinPoint.proceed();
        }
    }

    public Object handleCustomLockTimeoutStrategy(String customLockTimeoutStrategy, JoinPoint joinPoint) {
        Method currentMethod = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Object target = joinPoint.getTarget();
        Method handleMethod;
        try {
            handleMethod = target.getClass().getDeclaredMethod(customLockTimeoutStrategy, currentMethod.getParameterTypes());
            handleMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Illegal annotation param customLockTimeoutStrategy :" + customLockTimeoutStrategy, e);
        }
        Object[] args = joinPoint.getArgs();

        // invoke
        Object result;
        try {
            result = handleMethod.invoke(target, args);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Fail to illegal access custom lock timeout handler: " + customLockTimeoutStrategy, e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException("Fail to invoke custom lock timeout handler: " + customLockTimeoutStrategy, e);
        }
        return result;
    }
}
