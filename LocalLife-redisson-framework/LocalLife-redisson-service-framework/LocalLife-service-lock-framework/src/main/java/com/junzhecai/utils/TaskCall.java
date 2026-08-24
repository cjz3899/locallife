package com.junzhecai.utils;

//分布式锁 方法类型执行 有返回值的业务
@FunctionalInterface
public interface TaskCall<V> {

    /**
     * 执行任务
     *
     * @return 结果
     *
     */
    V call();
}
