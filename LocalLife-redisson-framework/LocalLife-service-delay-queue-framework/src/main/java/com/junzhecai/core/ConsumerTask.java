package com.junzhecai.core;

public interface ConsumerTask {
    void execute(String content);

    String topic();
}
