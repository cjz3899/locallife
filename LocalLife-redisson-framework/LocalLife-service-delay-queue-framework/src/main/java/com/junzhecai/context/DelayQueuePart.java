package com.junzhecai.context;

import com.junzhecai.core.ConsumerTask;
import lombok.Data;

/**
 * 延迟队列配置组件，组合基础配置与消费任务
 */
@Data
public class DelayQueuePart {
    private final DelayQueueBasePart delayQueueBasePart;

    private final ConsumerTask consumerTask;

    public DelayQueuePart(DelayQueueBasePart delayQueueBasePart, ConsumerTask consumerTask) {
        this.delayQueueBasePart = delayQueueBasePart;
        this.consumerTask = consumerTask;
    }
}
