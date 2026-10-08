package com.junzhecai.event;

import cn.hutool.core.collection.CollectionUtil;
import com.junzhecai.context.DelayQueueBasePart;
import com.junzhecai.context.DelayQueuePart;
import com.junzhecai.core.ConsumerTask;
import com.junzhecai.core.DelayConsumerQueue;
import lombok.AllArgsConstructor;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;

import java.util.Map;

/**
 * 事件监听器，监听ApplicationStartedEvent事件，在应用启动完成后初始化延迟队列
 * 相当于@PostConstruct注解
 */
@AllArgsConstructor
public class DelayQueueInitHandler implements ApplicationListener<ApplicationStartedEvent> {

    private final DelayQueueBasePart delayQueueBasePart;

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        Map<String, ConsumerTask> consumerTaskMap = event.getApplicationContext().getBeansOfType(ConsumerTask.class);
        if (CollectionUtil.isEmpty(consumerTaskMap)) {
            return;
        }
        for (ConsumerTask consumerTask : consumerTaskMap.values()) {
            DelayQueuePart delayQueuePart = new DelayQueuePart(delayQueueBasePart, consumerTask);
            Integer isolationRegionCount = delayQueuePart.getDelayQueueBasePart().getDelayQueueProperties().getIsolationRegionCount();
            for (int i = 0; i < isolationRegionCount; i++) {
                DelayConsumerQueue delayConsumerQueue = new DelayConsumerQueue(delayQueuePart, delayQueuePart.getConsumerTask().topic() + "-" + i);
                delayConsumerQueue.listenStart();
            }
        }
    }
}
