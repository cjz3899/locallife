package com.junzhecai.lockinfo.factory;

import com.junzhecai.lockinfo.LockInfoHandle;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class LockInfoHandleFactory implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    public LockInfoHandle getLockInfoHandle(String lockInfoType) {
        return applicationContext.getBean(lockInfoType, LockInfoHandle.class);
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
