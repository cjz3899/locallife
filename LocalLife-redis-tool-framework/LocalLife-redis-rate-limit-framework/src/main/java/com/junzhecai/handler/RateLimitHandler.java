package com.junzhecai.handler;

import com.junzhecai.enums.RateLimitScene;

public interface RateLimitHandler {
    void execute(Long voucherId, Long userId, RateLimitScene rateLimitScene);
}
