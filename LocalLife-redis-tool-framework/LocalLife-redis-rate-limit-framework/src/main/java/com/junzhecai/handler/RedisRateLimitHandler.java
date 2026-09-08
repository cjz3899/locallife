package com.junzhecai.handler;

import cn.hutool.core.util.StrUtil;
import com.junzhecai.config.SeckillRateLimitConfigProperties;
import com.junzhecai.core.RedisKeyManage;
import com.junzhecai.enums.BaseCode;
import com.junzhecai.enums.RateLimitScene;
import com.junzhecai.exception.LocalLifeFrameException;
import com.junzhecai.lua.RateLimitSlidingOperate;
import com.junzhecai.lua.TokenBucketRateLimitOperate;
import com.junzhecai.redis.RedisCache;
import com.junzhecai.redis.RedisKeyBuild;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
