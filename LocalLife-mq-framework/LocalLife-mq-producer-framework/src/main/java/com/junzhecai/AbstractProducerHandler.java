package com.junzhecai;

import com.alibaba.fastjson.JSON;
import com.junzhecai.message.MessageExtend;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.Assert;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

//Kafka 消息发送抽象处理器
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractProducerHandler<M extends MessageExtend<?>> {

    private final KafkaTemplate<String, M> kafkaTemplate;

    public final CompletableFuture<SendResult<String, M>> sendMqMessage(String topic, M message) {
        Assert.hasText(topic, "topic must not be blank");
        Assert.notNull(message, "message must not be null");
        try {
            CompletableFuture<SendResult<String, M>> future = kafkaTemplate.send(topic, message);
            return future.whenComplete((result, throwable) -> {
                if (throwable == null) {
                    afterSendSuccess(result);
                } else {
                    afterSendFailure(topic, message, throwable);
                }
            });
        } catch (Exception ex) {
            afterSendFailure(topic, message, ex);
            CompletableFuture<SendResult<String, M>> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        }
    }

    @SuppressWarnings("unchecked")
    public final <T> CompletableFuture<SendResult<String, M>> sendPayload(String topic, T payload) {
        M message = (M) MessageExtend.of(payload);
        return sendMqMessage(topic, message);
    }

    @SuppressWarnings("unchecked")
    public final <T> CompletableFuture<SendResult<String, M>> sendPayload(String topic, String key, T payload, Map<String, String> headers) {
        M message = (M) MessageExtend.of(payload, key, headers);
        return sendRecord(topic, message);
    }

    public final CompletableFuture<SendResult<String, M>> sendRecord(String topic, M message) {
        Assert.hasText(topic, "topic must not be blank");
        Assert.notNull(message, "message must not be null");

        ProducerRecord<String, M> record = new ProducerRecord<>(topic, message.getKey(), message);
        Map<String, String> headers = message.getHeaders();
        if (headers != null && !headers.isEmpty()) {
            headers.forEach((k, v) -> {
                if (Objects.nonNull(k) && Objects.nonNull(v)) {
                    record.headers().add(new RecordHeader(k, v.getBytes(StandardCharsets.UTF_8)));
                }
            });
        }

        try {
            CompletableFuture<SendResult<String, M>> future = kafkaTemplate.send(record);
            return future.whenComplete((result, throwable) -> {
                if (throwable == null) {
                    afterSendSuccess(result);
                } else {
                    afterSendFailure(topic, message, throwable);
                }
            });
        } catch (Exception ex) {
            afterSendFailure(topic, message, ex);
            CompletableFuture<SendResult<String, M>> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        }
    }

    @SuppressWarnings("unchecked")
    public final <T> CompletableFuture<Void> sendBatch(String topic, List<T> payloads) {
        Assert.hasText(topic, "topic must not be blank");
        Assert.notNull(payloads, "payloads must not be null");
        CompletableFuture<?>[] futures = payloads.stream()
                .map(p -> (M) MessageExtend.of(p))
                .map(m -> sendMqMessage(topic, m))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures);
    }

    public final <T> SendResult<String, M> sendAndWait(String topic, T payload) throws ExecutionException, InterruptedException {
        return sendPayload(topic, payload).get();
    }

    @SuppressWarnings("unchecked")
    public final <T> CompletableFuture<SendResult<String, M>> sendToDlq(String originalTopic, T payload, String reason) {
        String dlqTopic = originalTopic + ".DLQ";
        M message = (M) MessageExtend.of(payload);
        message.setHeaders(Map.of("dlqReason", reason));
        return sendRecord(dlqTopic, message);
    }

    protected void afterSendSuccess(SendResult<String, M> result) {
        log.info("kafka message send success, topic={}, partition={}, offset={}",
                result.getRecordMetadata().topic(), result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
    }

    protected void afterSendFailure(String topic, M message, Throwable throwable) {
        log.error("kafka message send failed, topic={}, message={}", topic, JSON.toJSON(message), throwable);
    }
}

