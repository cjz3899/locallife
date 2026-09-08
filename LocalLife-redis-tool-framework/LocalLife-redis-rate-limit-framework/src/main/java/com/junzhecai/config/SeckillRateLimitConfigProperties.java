package com.junzhecai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

@Data
@ConfigurationProperties(prefix = SeckillRateLimitConfigProperties.PREFIX)
public class SeckillRateLimitConfigProperties {
    public static final String PREFIX = "rate.limit";

    /**
     * 是否启动滑动窗口限流（默认不启动，使用令牌）
     */
    private Boolean enableSlidingWindow = false;

    /**
     * IP限流窗口毫秒数
     */
    private Integer ipWindowMills = 5000;

    /**
     * IP最大尝试次数
     */
    private Integer ipMaxAttempts = 3;

    /**
     * 用户限流窗口毫秒数
     */
    private Integer userWindowMills = 60000;

    /**
     * 用户最大尝试次数
     */
    private Integer userMaxAttempts = 5;

    /**
     * IP白名单列表，命中直接放行，不参与限流
     */
    private Set<String> ipWhiteList = Collections.emptySet();

    /**
     * 用户白名单列表，命中直接放行，不参与限流
     */
    private Set<Long> userWhiteList = Collections.emptySet();

    /**
     * 是否启动临时封禁惩罚策略（默认关闭）
     */
    private Boolean enablePenalty = false;

    /**
     * 统计违规（被限流阻断）计数的时间窗口
     */
    private Integer violationWindowSeconds = 60;

    /**
     * IP维度的封禁阈值（统计窗口内累计被阻断次数该值触发封禁）
     */
    private Integer ipBlockThreshold = 5;

    /**
     * 用户维度的封禁阈值（统计窗口内累计被阻断次数该值触发封禁）
     */
    private Integer userBlockThreshold = 5;

    /**
     * IP封禁TTL
     */
    private Integer ipBlockTtlSeconds = 300;

    /**
     * 用户封禁TTL
     */
    private Integer userBlockTtlSeconds = 300;

    /**
     * 发令牌接口的限流覆盖配置（为空则继承全局配置）
     */
    private EndpointLimit issue = new EndpointLimit();

    /**
     * 下单接口的限流覆盖配置（为空则继承全局配置）
     */
    private EndpointLimit seckill = new EndpointLimit();

    @Data
    public static class EndpointLimit implements Serializable {
        /**
         * IP限流窗口毫秒数（缺省时使用全局 ipWindowMillis）
         */
        private Integer ipWindowMillis;
        /**
         * IP最大尝试次数（缺省时使用全局 ipMaxAttempts）
         */
        private Integer ipMaxAttempts;
        /**
         * 用户限流窗口毫秒数（缺省时使用全局 userWindowMillis）
         */
        private Integer userWindowMillis;
        /**
         * 用户最大尝试次数（缺省时使用全局 userMaxAttempts）
         */
        private Integer userMaxAttempts;
    }


}
