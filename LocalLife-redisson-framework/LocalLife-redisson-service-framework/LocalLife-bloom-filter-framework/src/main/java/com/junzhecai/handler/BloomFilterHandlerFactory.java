package com.junzhecai.handler;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

//根据名称获取 BloomFilterHandler 的工厂
public class BloomFilterHandlerFactory implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    public BloomFilterHandler get(String name) {
        return applicationContext.getBean(name, BloomFilterHandler.class);
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}