package com.junzhecai.handler;

import cn.hutool.core.util.StrUtil;
import com.junzhecai.config.SeckillRateLimitConfigProperties;
import com.junzhecai.context.RateLimitContext;
import com.junzhecai.context.RateLimitScene;
import com.junzhecai.core.RedisKeyManage;
import com.junzhecai.enums.BaseCode;
import com.junzhecai.exception.LocalLifeFrameException;
import com.junzhecai.lua.RateLimitSlidingOperate;
import com.junzhecai.lua.TokenBucketRateLimitOperate;
import com.junzhecai.redis.RedisCache;
import com.junzhecai.redis.RedisKeyBuild;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@AllArgsConstructor
public class RedisRateLimitHandler implements RateLimitHandler {
    private final SeckillRateLimitConfigProperties seckillRateLimitConfigProperties;
    private final RedisCache redisCache;
    private final RateLimitSlidingOperate rateLimitSlidingOperate;
    private final TokenBucketRateLimitOperate tokenBucketRateLimitOperate;

    @Override
    public void execute(Long voucherId, Long userId, RateLimitScene rateLimitScene) {
        String clientIp = resolveClientIp();
        //验证白名单
        if (isWhitelisted(userId, clientIp)) {
            return;
        }
        //验证黑名单
        checkBans(voucherId, userId, clientIp);
        //IP限流窗口毫秒数
        int ipLimitWindowMills = resolveIpWindow(rateLimitScene);
        //IP最大尝试次数
        int ipLimitMaxAttempts = resolveIpMaxAttempts(rateLimitScene);
        //用户限流窗口毫秒数
        int userLimitWindowMills = resolveUserWindow(rateLimitScene);
        //用户最大尝试次数
        int userLimitMaxAttempts = resolveUserMaxAttempts(rateLimitScene);
        //是否启动滑动窗口限流，默认false，采用动态令牌桶
        Boolean userSliding = seckillRateLimitConfigProperties.getEnableSlidingWindow();
        //构建lua中的键
        List<String> keys = buildRateLimitKeys(voucherId, userId, clientIp, userSliding);
        //构建lua中的数据
        String[] args = buildArgs(ipLimitWindowMills, ipLimitMaxAttempts, userLimitWindowMills, userLimitMaxAttempts);

        RateLimitContext context = buildContext(voucherId, userId, clientIp, keys, userSliding, ipLimitWindowMills, ipLimitMaxAttempts, userLimitWindowMills, userLimitMaxAttempts);

        //执行限流，在lua中执行滑动窗口或者令牌的限流
        Integer result = executeLua(userSliding, keys, args);
        context.setResult(result);


    }

    private void handleResult(RateLimitContext context) {
        Integer result = context.getResult();
        if (BaseCode.SUCCESS.getCode().equals(result)) {

        }
    }

    private Integer executeLua(Boolean userSliding, List<String> keys, String[] args) {
        return userSliding ? rateLimitSlidingOperate.execute(keys, args).intValue() : tokenBucketRateLimitOperate.execute(keys, args).intValue();
    }

    private RateLimitContext buildContext(Long voucherId, Long userId, String clientIp,
                                          List<String> keys, boolean userSliding,
                                          int ipLimitWindowMills, int ipLimitMaxAttempts,
                                          int userLimitWindowMills, int userLimitMaxAttempts) {
        return RateLimitContext.builder()
                .voucherId(voucherId)
                .userId(userId)
                .clientIp(clientIp)
                .keys(keys)
                .userSliding(userSliding)
                .ipLimitWindowMills(ipLimitWindowMills)
                .ipLimitMaxAttempts(ipLimitMaxAttempts)
                .userLimitWindowMills(userLimitWindowMills)
                .userLimitMaxAttempts(userLimitMaxAttempts)
                .build();
    }

    private String[] buildArgs(int ipLimitWindowMills, int ipLimitMaxAttempts, int userLimitWindowMills, int userLimitMaxAttempts) {
        String[] args = new String[4];
        args[0] = String.valueOf(ipLimitWindowMills);
        args[1] = String.valueOf(ipLimitMaxAttempts);
        args[2] = String.valueOf(userLimitWindowMills);
        args[3] = String.valueOf(userLimitMaxAttempts);
        return args;
    }

    private List<String> buildRateLimitKeys(Long voucherId, Long userId, String clientIp, boolean userSliding) {
        List<String> keys = new ArrayList<>(2);
        if (Objects.nonNull(clientIp)) {
            String ipKey = userSliding ? RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_LIMIT_IP_SW_TAG_KEY, voucherId, clientIp).getRelKey()
                    : RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_LIMIT_IP_TB_TAG_KEY, voucherId, clientIp).getRelKey();
            keys.add(ipKey);
        }
        if (Objects.nonNull(userId)) {
            String userKey = userSliding ? RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_LIMIT_USER_SW_TAG_KEY, voucherId, userId).getRelKey()
                    : RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_LIMIT_USER_TB_TAG_KEY, voucherId, userId).getRelKey();
            keys.add(userKey);
        }
        return keys;
    }


    private int resolveIpWindow(RateLimitScene scene) {
        SeckillRateLimitConfigProperties.EndpointLimit ep =
                scene == RateLimitScene.ISSUE_TOKEN ? seckillRateLimitConfigProperties.getIssue() :
                        seckillRateLimitConfigProperties.getSeckill();
        Integer v = ep != null ? ep.getIpWindowMillis() : null;
        return v != null ? v : seckillRateLimitConfigProperties.getIpWindowMills();
    }

    private int resolveIpMaxAttempts(RateLimitScene scene) {
        SeckillRateLimitConfigProperties.EndpointLimit ep =
                scene == RateLimitScene.ISSUE_TOKEN ? seckillRateLimitConfigProperties.getIssue() :
                        seckillRateLimitConfigProperties.getSeckill();
        Integer v = ep != null ? ep.getIpMaxAttempts() : null;
        return v != null ? v : seckillRateLimitConfigProperties.getIpMaxAttempts();
    }

    private int resolveUserWindow(RateLimitScene scene) {
        SeckillRateLimitConfigProperties.EndpointLimit ep =
                scene == RateLimitScene.ISSUE_TOKEN ? seckillRateLimitConfigProperties.getIssue() :
                        seckillRateLimitConfigProperties.getSeckill();
        Integer v = ep != null ? ep.getUserWindowMillis() : null;
        return v != null ? v : seckillRateLimitConfigProperties.getUserWindowMills();
    }

    private int resolveUserMaxAttempts(RateLimitScene scene) {
        SeckillRateLimitConfigProperties.EndpointLimit ep =
                scene == RateLimitScene.ISSUE_TOKEN ? seckillRateLimitConfigProperties.getIssue() :
                        seckillRateLimitConfigProperties.getSeckill();
        Integer v = ep != null ? ep.getUserMaxAttempts() : null;
        return v != null ? v : seckillRateLimitConfigProperties.getUserMaxAttempts();
    }

    private void checkBans(Long voucherId, Long userId, String clientIp) {
        if (Objects.nonNull(clientIp)) {
            Boolean ipBlocked = redisCache.hasKey(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_BLOCK_IP_TAG_KEY));
            if (ipBlocked) {
                throw new LocalLifeFrameException(BaseCode.SECKILL_RATE_LIMIT_IP_EXCEEDED);
            }
        }
        if (Objects.nonNull(userId)) {
            Boolean userBlocked = redisCache.hasKey(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_BLOCK_USER_TAG_KEY));
            if (userBlocked) {
                throw new LocalLifeFrameException(BaseCode.SECKILL_RATE_LIMIT_USER_EXCEEDED);
            }
        }
    }

    private boolean isWhitelisted(Long userId, String clientIp) {
        try {
            return (StrUtil.isNotBlank(clientIp) && seckillRateLimitConfigProperties.getIpWhiteList() != null)
                    || (Objects.nonNull(userId) && seckillRateLimitConfigProperties.getUserWhiteList() != null
                    && seckillRateLimitConfigProperties.getUserWhiteList().contains(userId));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取客户端IP地址
     */
    private String resolveClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest request = attrs.getRequest();
            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isEmpty()) {
                String[] parts = xff.split(",");
                if (parts.length > 0) {
                    String ip = parts[0].trim();
                    if (!ip.isEmpty()) {
                        return ip;
                    }
                }
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isEmpty()) {
                return realIp;
            }
            return request.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
