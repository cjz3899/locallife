package com.junzhecai.core;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 轮询0->1->2->3->4...
 */
public class IsolationRegionSelector {
    private final AtomicInteger count = new AtomicInteger(0);

    private final Integer thresholdValue;

    public IsolationRegionSelector(Integer thresholdValue) {
        this.thresholdValue = thresholdValue;
    }

    private int reset() {
        count.set(0);
        return count.get();
    }

    public synchronized int getIndex() {
        int cur = count.get();
        if (cur >= thresholdValue) {
            cur = reset();
        } else {
            count.incrementAndGet();
        }
        return cur;
    }
}
