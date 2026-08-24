package com.junzhecai.config;

import cn.hutool.core.collection.CollectionUtil;
import com.junzhecai.handler.BloomFilterHandler;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;

//根据配置在 Bean 定义阶段注册多个
public class BloomFilterHandlerRegistrar implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

    private final Environment environment;

    public BloomFilterHandlerRegistrar(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(@NonNull BeanDefinitionRegistry registry) throws BeansException {
        //解析配置，从配置中读取bloom-filter.filters的映射，k：业务名，v：过滤器
        Map<String, BloomFilterProperties.Filter> filters = resolveFiltersFromEnvironment();
        if (CollectionUtil.isEmpty(filters)) {
            return;
        }
        filters.forEach((alias, cfg) -> {
            //获取Bean别名
            String beanName = StringUtils.hasText(cfg.getName()) ? cfg.getName() : alias;

            RootBeanDefinition bd = new RootBeanDefinition(BloomFilterHandler.class);
            bd.getConstructorArgumentValues().addIndexedArgumentValue(0, new RuntimeBeanReference("redissonClient"));
            bd.getConstructorArgumentValues().addIndexedArgumentValue(1, beanName);
            bd.getConstructorArgumentValues().addIndexedArgumentValue(2, cfg.getExpectedInsertions());
            bd.getConstructorArgumentValues().addIndexedArgumentValue(3, cfg.getFalseProbability());

            registry.registerBeanDefinition(beanName, bd);
            if (!beanName.equals(alias)) {
                registry.registerAlias(beanName, alias);
            }
        });
    }

    @Override
    public void postProcessBeanFactory(@NonNull ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // no-op
    }

    //最高优先级
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private Map<String, BloomFilterProperties.Filter> resolveFiltersFromEnvironment() {
        Binder binder = Binder.get(environment);
        return binder.bind("bloom-filter.filters",
                        Bindable.mapOf(String.class, BloomFilterProperties.Filter.class))
                .orElse(Collections.emptyMap());
    }
}
