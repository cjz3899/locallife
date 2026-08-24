package com.junzhecai.kafka.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

//秒杀券缓存失效广播消息
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeckillVoucherInvalidationMessage {

    private Long voucherId;

    private String reason;
}
