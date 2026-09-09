package com.junzhecai.context;

import lombok.Data;

import java.util.List;

@Data
public class RateLimitContext {
    private Long voucherId;

    private Long userId;

    private String clientIp;

    private List<String> keys;

    private boolean userSliding;

    private Integer ipLimitWindowMills;

    private Integer ipLimitMaxAttempts;

    private Integer userLimitWindowMills;

    private Integer userLimitMaxAttempts;

    private boolean result;
}
