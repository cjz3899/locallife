package com.junzhecai.config;

import com.junzhecai.toolkit.SnowflakeIdGenerator;
import com.junzhecai.toolkit.WorkAndDataCenterIdHandler;
import com.junzhecai.toolkit.WorkDataCenterId;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

//分布式id配置
public class IdGeneratorAutoConfig {

    @Bean
    public WorkAndDataCenterIdHandler workAndDataCenterIdHandler(StringRedisTemplate stringRedisTemplate) {
        return new WorkAndDataCenterIdHandler(stringRedisTemplate);
    }

    @Bean
    public WorkDataCenterId workDataCenterId(WorkAndDataCenterIdHandler workAndDataCenterIdHandler) {
        return workAndDataCenterIdHandler.getWorkAndDataCenterId();
    }

    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator(WorkDataCenterId workDataCenterId) {
        return new SnowflakeIdGenerator(workDataCenterId);
    }
}
