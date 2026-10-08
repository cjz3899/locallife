package com.junzhecai.context;

import com.junzhecai.core.DelayProduceQueue;
import com.junzhecai.core.IsolationRegionSelector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 延迟队列生产端组合器，按隔离区域分片将消息投递到对应的延迟队列
 */
public class DelayQueueProduceCombine {
    private final IsolationRegionSelector isolationRegionSelector;

    private final List<DelayProduceQueue> delayProduceQueueList = new ArrayList<>();

    public DelayQueueProduceCombine(DelayQueueBasePart delayQueueBasePart, String topic) {
        Integer isolationRegionCount = delayQueueBasePart.getDelayQueueProperties().getIsolationRegionCount();
        isolationRegionSelector = new IsolationRegionSelector(isolationRegionCount);
        for (int i = 0; i < isolationRegionCount; i++) {
            delayProduceQueueList.add(new DelayProduceQueue(delayQueueBasePart.getRedissonClient(), topic + "-" + i));
        }
    }

    public void offer(String content, long delayTime, TimeUnit timeUnit) {
        //通过选择器获取索引，分区投递
        int index = isolationRegionSelector.getIndex();
        //将消息投递到对应的延迟队列
        delayProduceQueueList.get(index).offer(content, delayTime, timeUnit);
    }
}
