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

/**
 * Kafka 消息发送抽象处理器
 * <p>
 * 封装了基于 {@link KafkaTemplate} 的发送能力，统一对消息进行 {@link MessageExtend} 包装，
 * 并提供成功与失败的扩展钩子方法（{@link #afterSendSuccess(SendResult)}、{@link #afterSendFailure(String, MessageExtend, Throwable)}）。
 * 同时提供多种便捷发送方式：直接传入业务载荷、携带 key 与 headers、批量发送、阻塞发送、以及发送到 DLQ
 * </p>
 *
 * @param <M> 实际发送的消息类型，必须是 {@link MessageExtend} 的子类型（载荷类型可不固定）
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractProducerHandler<M extends MessageExtend<?>> {
    //Kafka 模板
    private final KafkaTemplate<String, M> kafkaTemplate;

    /**
     * 发送消息（已包装为 {@link MessageExtend}），并返回异步结果。
     * <p>方法内部会在发送完成后调用扩展钩子：成功则调用 {@link #afterSendSuccess(SendResult)}；
     * 失败则调用 {@link #afterSendFailure(String, MessageExtend, Throwable)}。</p>
     * <p>同时对同步异常进行处理：若 {@code kafkaTemplate.send(...)} 在提交阶段直接抛出异常，
     * 会立即触发 {@link #afterSendFailure(String, MessageExtend, Throwable)}，并返回一个已完成异常的 {@link CompletableFuture}。</p>
     *
     * @param topic   目标主题，不能为空
     * @param message 已包装的消息对象，不能为空
     * @return 异步发送结果 {@link CompletableFuture}，包含 {@link SendResult}
     */
    public final CompletableFuture<SendResult<String, M>> sendMqMessage(String topic, M message) {
        Assert.hasText(topic, "topic must not be blank");
        Assert.notNull(message, "message must not be null");
        try {
            CompletableFuture<SendResult<String, M>> future = kafkaTemplate.send(topic, message);
            //异步发送失败
            /*KafkaTemplate.send()正常返回，但消息实际发送失败
             * 通过future.whenComplete()回调处理*/
            return future.whenComplete((result, throwable) -> {
                if (throwable == null) {
                    afterSendSuccess(result);
                } else {
                    afterSendFailure(topic, message, throwable);
                }
            });
            //同步发送失败
            /*KafkaTemplate.send()直接抛异常，消息发送失败
             *failed.completeExceptionally(ex)手动标记异常完成状态*/
        } catch (Exception ex) {
            afterSendFailure(topic, message, ex);
            CompletableFuture<SendResult<String, M>> failed = new CompletableFuture<>();
            failed.completeExceptionally(ex);
            return failed;
        }
    }

    /**
     * 直接传入业务载荷，内部自动包装为 {@link MessageExtend} 并发送。
     *
     * @param <T>     业务载荷类型
     * @param topic   目标主题，不能为空
     * @param payload 业务载荷，不能为空
     * @return 异步发送结果 {@link CompletableFuture}
     */
    public final <T> CompletableFuture<SendResult<String, M>> sendPayload(String topic, T payload) {
        M message = (M) MessageExtend.of(payload);
        return sendMqMessage(topic, message);
    }

    /**
     * 传入业务载荷并指定消息 key 与 headers，内部包装为 {@link MessageExtend} 并发送
     *
     * @param <T>     业务载荷类型
     * @param topic   目标主题，不能为空
     * @param key     消息键（用于分区与幂等），可为空
     * @param payload 业务载荷，不能为空
     * @param headers 业务元数据（将映射到 Kafka Headers），可为空或空集合
     * @return 异步发送结果 {@link CompletableFuture}
     */
    public final <T> CompletableFuture<SendResult<String, M>> sendPayload(String topic, String key, T payload, Map<String, String> headers) {
        M message = (M) MessageExtend.of(payload, key, headers);
        return sendRecord(topic, message);
    }

    /**
     * 基于 {@link ProducerRecord} 进行发送
     * <p>会将 {@link MessageExtend#getKey()} 用作 record 的 key；将 {@link MessageExtend#getHeaders()} 映射到 Kafka Headers，
     * 采用 UTF-8 编码。空键或空值的 header 会被忽略</p>
     *
     * @param topic   目标主题，不能为空
     * @param message 已包装的消息对象，不能为空
     * @return 异步发送结果 {@link CompletableFuture}
     */
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

    /**
     * 批量发送：按顺序触发发送，并返回聚合的 {@link CompletableFuture}
     * <p>注意：该方法不保证原子性；如需逐条错误处理，请使用返回的各个 future
     * 返回的 {@code CompletableFuture<Void>} 仅表示“全部完成”的状态</p>
     *
     * @param <T>      业务载荷类型
     * @param topic    目标主题，不能为空
     * @param payloads 载荷列表，不能为空
     * @return 聚合的 {@link CompletableFuture}，全部子任务完成后结束
     */
    public final <T> CompletableFuture<Void> sendBatch(String topic, List<T> payloads) {
        Assert.hasText(topic, "topic must not be blank");
        Assert.notNull(payloads, "payloads must not be null");
        CompletableFuture<?>[] futures = new CompletableFuture<?>[payloads.size()];
        for (int i = 0; i < payloads.size(); i++) {
            M message = (M) MessageExtend.of(payloads.get(i));
            futures[i] = sendMqMessage(topic, message);
        }
        return CompletableFuture.allOf(futures);
    }

    /**
     * 阻塞发送：调用方需要同步等待发送结果（broker ack）时使用
     *
     * @param <T>     业务载荷类型
     * @param topic   目标主题，不能为空
     * @param payload 业务载荷，不能为空
     * @return 发送结果 {@link SendResult}
     * @throws ExecutionException   发送失败或异步执行失败
     * @throws InterruptedException 当前线程在等待结果时被中断
     */
    public final <T> SendResult<String, M> sendAndWait(String topic, T payload) throws ExecutionException, InterruptedException {
        return sendPayload(topic, payload).get();
    }


    /**
     * 发送到死信队列（DLQ），topic 采用约定：原 topic + ".DLQ"。
     *
     * @param <T>           业务载荷类型
     * @param originalTopic 原始主题
     * @param payload       业务载荷
     * @param reason        死信原因，会作为 header 写入（键：dlqReason）
     * @return 异步发送结果 {@link CompletableFuture}
     */
    public final <T> CompletableFuture<SendResult<String, M>> sendToDlq(String originalTopic, T payload, String reason) {
        String dlqTopic = originalTopic + ".DLQ";
        M message = (M) MessageExtend.of(payload);
        message.setHeaders(Map.of("dlqReason", reason));
        return sendRecord(dlqTopic, message);
    }

    /**
     * 发送成功后的扩展钩子，默认记录日志；子类可重写进行自定义处理
     *
     * @param result 发送结果，包含主题、分区、offset 等元数据
     */
    protected void afterSendSuccess(SendResult<String, M> result) {
        log.info("kafka message send success, topic={}, partition={}, offset={}",
                result.getRecordMetadata().topic(), result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
    }

    /**
     * 发送失败后的扩展钩子，默认记录错误日志；子类可重写进行自定义处理（如告警、重试、DLQ）
     *
     * @param topic     发送的目标主题
     * @param message   发送的消息对象
     * @param throwable 失败异常
     */
    protected void afterSendFailure(String topic, M message, Throwable throwable) {
        log.error("kafka message send failed, topic={}, message={}", topic, JSON.toJSON(message), throwable);
    }
}

