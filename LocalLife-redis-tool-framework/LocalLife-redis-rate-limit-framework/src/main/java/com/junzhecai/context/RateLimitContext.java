package com.junzhecai.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
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

    private Integer result;
}
