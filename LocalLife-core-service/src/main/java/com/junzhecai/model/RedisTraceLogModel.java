package com.junzhecai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
//日志模型，数据库中
public class RedisTraceLogModel {

    private String logType;

    private Long ts;

    private String orderId;

    private String traceId;

    private String userId;

    private String voucherId;

    private Integer beforeQty;

    private Integer changeQty;

    private Integer afterQty;
}
