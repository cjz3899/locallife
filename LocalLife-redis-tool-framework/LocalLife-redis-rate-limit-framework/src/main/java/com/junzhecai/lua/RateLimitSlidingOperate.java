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
public class RateLimitSlidingOperate {
    private final RedisCache redisCache;
    private DefaultRedisScript<Integer> redisScript;

    public RateLimitSlidingOperate(RedisCache redisCache) {
        this.redisCache = redisCache;
    }

    @PostConstruct
    public void init() {
        redisScript = new DefaultRedisScript<>();
        redisScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("lua/rateLimitSliding.lua"))
        );
        redisScript.setResultType(Integer.class);
        log.info("滑动窗口限流 Lua 脚本完成");
    }

    public Long execute(List<String> keys, String[] args) {
        return (Long) redisCache.getInstance().execute(redisScript, keys, (Object[]) args);
    }
}
