package com.junzhecai.handler;

import com.junzhecai.extension.RateLimitScene;

public interface RateLimitHandler {
    void execute(Long voucherId, Long userId, RateLimitScene rateLimitScene);
}
