package com.junzhecai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

//布隆过滤器配置属性
@Data
@ConfigurationProperties(prefix = BloomFilterProperties.PREFIX)
public class BloomFilterProperties {

    public static final String PREFIX = "bloom-filter";

    private Map<String, Filter> filters;

    @Data
    public static class Filter {
        private String name;
        private Long expectedInsertions = 20000L;
        private Double falseProbability = 0.01D;
    }
}

