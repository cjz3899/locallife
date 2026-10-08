package com.junzhecai.core;

import com.junzhecai.context.DelayQueuePart;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class DelayConsumerQueue extends DelayBaseQueue {

    /**
     * 监听消息线程数
     */
    private final AtomicInteger listenStartThreadCount = new AtomicInteger(1);

    /**
     * 消费消息线程数
     */
    private final AtomicInteger executeTaskThreadCount = new AtomicInteger(1);

    /**
     * 监听消息的线程池
     */
    private final ThreadPoolExecutor listenStartThreadPool;

    /**
     * 消费消息的线程池
     */
    private final ThreadPoolExecutor executeTaskThreadPool;

    /**
     * 监控消费启动标识
     */
    private final AtomicBoolean runFlag = new AtomicBoolean(false);

    private final ConsumerTask consumerTask;

    public DelayConsumerQueue(DelayQueuePart delayQueuePart, String relTopic) {
        super(delayQueuePart.getDelayQueueBasePart().getRedissonClient(), relTopic);
        this.listenStartThreadPool = new ThreadPoolExecutor(1, 1, 60,
                TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> new Thread(Thread.currentThread().getThreadGroup(), r,
                "listen-start-thread-" + listenStartThreadCount.getAndIncrement()));
        this.executeTaskThreadPool = new ThreadPoolExecutor(
                delayQueuePart.getDelayQueueBasePart().getDelayQueueProperties().getCorePoolSize(),
                delayQueuePart.getDelayQueueBasePart().getDelayQueueProperties().getMaximumPoolSize(),
                delayQueuePart.getDelayQueueBasePart().getDelayQueueProperties().getKeepAliveTime(),
                delayQueuePart.getDelayQueueBasePart().getDelayQueueProperties().getUnit(),
                new LinkedBlockingQueue<>(delayQueuePart.getDelayQueueBasePart().getDelayQueueProperties().getWorkQueueSize()),
                r -> new Thread(Thread.currentThread().getThreadGroup(), r,
                        "delay-queue-consume-thread-" + executeTaskThreadCount.getAndIncrement()));
        this.consumerTask = delayQueuePart.getConsumerTask();
    }

    /**
     * 启动消息监听
     */
    public synchronized void listenStart() {
        if (!runFlag.get()) {
            runFlag.set(true);
            //异步执行监听逻辑
            listenStartThreadPool.execute(() -> {
                while (!Thread.interrupted()) {
                    try {
                        if (blockingQueue == null) {
                            throw new IllegalStateException("blockingQueue 未初始化");
                        }
                        String content = blockingQueue.take();
                        //监听到消息，执行处理逻辑
                        executeTaskThreadPool.execute(() -> {
                            try {
                                consumerTask.execute(content);
                            } catch (Exception e) {
                                log.error("consumer execute error", e);
                            }
                        });
                    } catch (InterruptedException e) {
                        destroy(executeTaskThreadPool);
                    } catch (Throwable e) {
                        log.error("blockingQueue take error", e);
                    }
                }
            });
        }
    }

    public void destroy(ExecutorService executorService) {
        try {
            if (Objects.nonNull(executorService)) {
                executorService.shutdown();
            }
        } catch (Exception e) {
            log.error("destroy error", e);
        }
    }
}
