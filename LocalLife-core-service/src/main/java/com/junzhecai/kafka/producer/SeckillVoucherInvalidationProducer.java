package com.junzhecai.kafka.producer;

import com.junzhecai.AbstractProducerHandler;
import com.junzhecai.kafka.message.SeckillVoucherInvalidationMessage;
import com.junzhecai.message.MessageExtend;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.support.PropertiesLoaderSupport;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

//Kafka生产者：广播秒杀券缓存失效消息
@Slf4j
@Component
public class SeckillVoucherInvalidationProducer extends AbstractProducerHandler<MessageExtend<SeckillVoucherInvalidationMessage>> {

    private static final String RETRY_COUNT = "retryCount";

    private static final String DLQ = ".DLQ";

    @Resource
    private PropertiesLoaderSupport propertiesLoaderSupport;

    public SeckillVoucherInvalidationProducer(KafkaTemplate<String, MessageExtend<SeckillVoucherInvalidationMessage>> kafkaTemplate) {
        super(kafkaTemplate);
    }

    @Resource
    private MeterRegistry meterRegistry;

    @Value("${seckill.cache.invalidate.retry.maxAttempts:3}")
    private int retryMaxAttempts;

    @Value("${seckill.cache.invalidate.retry.initialBackoffMillis:200}")
    private long initialBackoffMillis;

    @Value("${seckill.cache.invalidate.retry.maxBackoffMillis:800}")
    private long maxBackoffMillis;

    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");


    @Override
    protected void afterSendFailure(String topic, MessageExtend<SeckillVoucherInvalidationMessage> message, Throwable throwable) {
        SeckillVoucherInvalidationMessage body = message.getMessageBody();
        Long voucherId = body.getVoucherId();
        String reason = body.getReason();
        String errMsg = throwable == null ? "unknown" : throwable.getMessage();
        log.error("SeckillVoucherInvalidation send failed, topic={}, uuid={}, key={}, voucherId={}, reason={}, error= {}",
                topic, message.getUuid(), message.getKey(), voucherId, reason, errMsg, throwable);
        if (topic.contains(DLQ)) {
            safeInc("seckill_invalidation_dlq", "reason", "send_failures");
            return;
        } else {
            safeInc("seckill_invalidation_send_failures", "topic", topic);
        }


        Map<String, String> headers = message.getHeaders();
        headers = headers == null ? new HashMap<>(8) : new HashMap<>(headers);
        int retryCount = 0;
        try {
            if (headers.containsKey(RETRY_COUNT)) {
                retryCount = Integer.parseInt(headers.get(RETRY_COUNT));
            }
        } catch (Exception ignore) {
        }

        if (retryCount < retryMaxAttempts) {
            long backoff = Math.min(initialBackoffMillis * (1L << retryCount), maxBackoffMillis);
            headers.put(RETRY_COUNT, String.valueOf(retryCount + 1));
            headers.put("lastError", truncate(errMsg));
            message.setHeaders(headers);
            log.warn("Retry sending cache invalidation, topic={}, uuid={}, voucherId={}, retryCount={}, backoffMs={}",
                    topic, message.getUuid(), voucherId, retryCount + 1, backoff);
            safeInc("seckill_invalidation_send_retries", "topic", topic);
            sleepQuietly(backoff);
            sendRecord(topic, message);
            return;
        }

        String dlqReason = "send_invalid_cache_broadcast_failed: " + truncate(errMsg);
        try {
            sendToDlq(topic, body, dlqReason);
            log.warn("Send cache invalidation to DLQ, originalTopic={}, uuid={}, voucherId={}, dlqReason={}",
                    topic, message.getUuid(), voucherId, dlqReason);
            auditLog.warn("DLQ_PUBLISH|topic={}|uuid={}|key={}|voucherId={}|reason={}",
                    topic, message.getUuid(), message.getKey(), voucherId, dlqReason);
            safeInc("seckill_invalidation_send_dlq", "topic", topic);
        } catch (Exception e) {
            log.error("Send cache invalidation to DLQ failed, originalTopic={}, uuid={}, voucherId={}, error={}",
                    topic, message.getUuid(), voucherId, e.getMessage(), e);
            safeInc("seckill_invalidation_send_dlq_failures", "topic", topic);
        }
    }

    @Override
    protected void afterSendSuccess(SendResult<String, MessageExtend<SeckillVoucherInvalidationMessage>> result) {
        super.afterSendSuccess(result);
        String topic = result.getRecordMetadata().topic();
        MessageExtend<SeckillVoucherInvalidationMessage> message = result.getProducerRecord().value();
        boolean dlqReplay = message != null && message.getHeaders() != null && "1".equals(message.getHeaders().getOrDefault("dlqReplayCount", "0"));
        safeInc("seckill_invalidation_send_success", "topic", topic);
        if (dlqReplay) {
            safeInc("seckill_invalidation_dlq_replay_success", "topic", topic);
            auditLog.info("DLQ_REPLAY_SUCCESS|topic={}|uuid={}|key={}|voucherId={}",
                    topic, message.getUuid(), message.getKey(), message.getMessageBody().getVoucherId());
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 256 ? s : s.substring(0, 256);
    }

    private void sleepQuietly(long backoffMs) {
        try {
            TimeUnit.MILLISECONDS.sleep(backoffMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private void safeInc(String name, String tagKey, String tagValue) {
        try {
            if (meterRegistry != null) {
                meterRegistry.counter(name, tagKey, tagValue).increment();
            }
        } catch (Exception ignore) {
        }
    }
}
