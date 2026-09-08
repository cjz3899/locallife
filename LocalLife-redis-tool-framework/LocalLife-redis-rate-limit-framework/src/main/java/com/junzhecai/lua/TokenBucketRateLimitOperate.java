package com.junzhecai.lua;

import com.junzhecai.redis.RedisCache;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.List;

@Slf4j
@Getter
public class TokenBucketRateLimitOperate {
    private final RedisCache redisCache;

    public TokenBucketRateLimitOperate(RedisCache redisCache) {
        this.redisCache = redisCache;
    }

    private DefaultRedisScript<Integer> redisScript;

    @PostConstruct
    public void init() {
        redisScript = new DefaultRedisScript<>();
        redisScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("lua/tokenBucket.lua"))
        );
        redisScript.setResultType(Integer.class);
        log.info("令牌桶限流lua脚本完成");
    }

    public Long execute(List<String> keys, String[] args) {
        return (Long) redisCache.getInstance().execute(redisScript, keys, (Object[]) args);
    }

}
