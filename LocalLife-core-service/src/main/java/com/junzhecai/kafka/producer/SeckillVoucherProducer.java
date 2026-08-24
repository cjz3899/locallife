package com.junzhecai.kafka.producer;

import com.junzhecai.AbstractProducerHandler;
import com.junzhecai.enums.SeckillVoucherOrderOperate;
import com.junzhecai.kafka.message.SeckillVoucherMessage;
import com.junzhecai.kafka.redis.RedisVoucherData;
import com.junzhecai.message.MessageExtend;
import com.junzhecai.toolkit.SnowflakeIdGenerator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

//Kafka 生产者：发送秒杀券
@Slf4j
@Component
public class SeckillVoucherProducer extends AbstractProducerHandler<MessageExtend<SeckillVoucherMessage>> {

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;


    @Resource
    private RedisVoucherData redisVoucherData;

    public SeckillVoucherProducer(final KafkaTemplate<String, MessageExtend<SeckillVoucherMessage>> kafkaTemplate) {
        super(kafkaTemplate);
    }

    @Override
    protected void afterSendFailure(final String topic, final MessageExtend<SeckillVoucherMessage> message, final Throwable throwable) {
        super.afterSendFailure(topic, message, throwable);
        long traceId = snowflakeIdGenerator.nextId();
        redisVoucherData.rollbackRedisVoucherData(
                SeckillVoucherOrderOperate.YES,
                traceId,
                message.getMessageBody().getVoucherId(),
                message.getMessageBody().getUserId(),
                message.getMessageBody().getOrderId(),
                message.getMessageBody().getAfterQty(),
                message.getMessageBody().getChangeQty(),
                message.getMessageBody().getBeforeQty());
    }
}

