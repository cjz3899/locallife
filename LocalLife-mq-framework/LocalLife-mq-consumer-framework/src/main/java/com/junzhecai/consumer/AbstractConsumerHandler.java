package com.junzhecai.consumer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.junzhecai.message.MessageExtend;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 消费者抽象基类
 * <p>统一将消息值（JSON 字符串）转换为 {@link MessageExtend}，并提供消费成功/失败的扩展钩子
 * 子类只需实现 {@link #doConsume(MessageExtend)} 并在方法上使用 {@code @KafkaListener} 注解，
 * 然后在监听方法中调用 {@link #consumeRaw(String, Map)} 或 {@link #consume(MessageExtend)} 即可</p>
 * <p>推荐用法：</p>
 * <pre>
 * {@code
 * @KafkaListener(topics = "order-topic", groupId = "order-group")
 * public void onMessage(String value, @Headers Map<String, Object> headers) {
 *     consumeRaw(value, headers);
 * }
 * }
 * </pre>
 *
 * @param <T> 业务载荷类型
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractConsumerHandler<T> {

    //为了在运行时保留泛型类型信息，使得代码能够在运行时正确地进行类型转换和反序列化操作
    private final Class<T> payloadType;

    /**
     * 原始值消费入口：将字符串值与 Kafka headers 解析为 {@link MessageExtend}，并触发消费流程
     *
     * @param value   Kafka 消息值（JSON 字符串）
     * @param headers Kafka headers（可能包含字节数组），会被转换为字符串 Map
     */
    public final void consumeRaw(String value, Map<String, Object> headers) {
        MessageExtend<T> message = convert(value, toStringHeaders(headers));
        consume(message);
    }

    /**
     * 原始值消费入口（含 key）：当监听方法能获取 record key 时使用此重载
     *
     * @param value   Kafka 消息值（JSON 字符串）
     * @param key     Kafka record key
     * @param headers Kafka headers（可能包含字节数组），会被转换为字符串 Map
     */
    public final void consumeRaw(String value, String key, Map<String, Object> headers) {
        MessageExtend<T> message = convert(value, toStringHeaders(headers));
        message.setKey(key);
        consume(message);
    }

    /**
     * 统一消费入口：触发核心消费逻辑，并在异常时调用失败钩子
     *
     * @param message 解析后的消息对象
     */
    public final void consume(MessageExtend<T> message) {
        Boolean result = beforeConsume(message);
        try {
            if (result) {
                doConsume(message);
            } else {
                return;
            }
        } catch (Throwable t) {
            afterConsumeFailure(message, t);
            throw t;
        }
        afterConsumeSuccess(message);
    }

    /**
     * 真正消费前的前置钩子，默认打印日志
     *
     * @param message 成功消费的消息
     */
    protected Boolean beforeConsume(MessageExtend<T> message) {
        log.info("kafka message before consume success, uuid={}, key={}", message.getUuid(), message.getKey());
        return true;
    }

    /**
     * 子类实现的核心消费逻辑。
     *
     * @param message 解析后的消息对象
     */
    protected abstract void doConsume(MessageExtend<T> message);

    /**
     * 消费成功的扩展钩子，默认打印日志。
     *
     * @param message 成功消费的消息
     */
    protected void afterConsumeSuccess(MessageExtend<T> message) {
        log.info("kafka message consume success, uuid={}, key={}", message.getUuid(), message.getKey());
    }

    /**
     * 消费失败的扩展钩子，默认打印错误日志
     *
     * @param message   消费失败的消息
     * @param throwable 失败异常
     */
    protected void afterConsumeFailure(MessageExtend<T> message, Throwable throwable) {
        log.error("kafka message consume failed, uuid={}, key={}, messageBody={}",
                message.getUuid(), message.getKey(), JSON.toJSONString(message.getMessageBody()), throwable);
    }

    /**
     * 将 Kafka headers（Object 值）转换为字符串 Map，字节数组采用 UTF-8 解码，其他类型使用 {@code toString()}
     *
     * @param headers Kafka headers（可能包含 byte[]）
     * @return 字符串 Map
     */
    protected Map<String, String> toStringHeaders(Map<String, Object> headers) {
        Map<String, String> map = new HashMap<>();
        if (headers == null || headers.isEmpty()) {
            return map;
        }
        headers.forEach((k, v) -> {
            if (v == null) {
                return;
            }
            if (v instanceof byte[] bytes) {
                map.put(k, new String(bytes, StandardCharsets.UTF_8));
            } else {
                map.put(k, v.toString());
            }
        });
        return map;
    }

    /**
     * 将 JSON 字符串解析为 {@link MessageExtend}，并按 {@link #payloadType} 将 messageBody 解析为具体类型。
     *
     * @param value   Kafka 消息值（JSON 字符串）
     * @param headers Kafka headers（字符串 Map），将写入 {@link MessageExtend#setHeaders(Map)}
     * @return 解析后的消息对象
     */
    public MessageExtend<T> convert(String value, Map<String, String> headers) {
        JSONObject root = JSON.parseObject(value);
        Object rawBody = root.get("messageBody");
        T body = rawBody == null ? null : JSON.parseObject(JSON.toJSONString(rawBody), payloadType);

        MessageExtend<T> message = new MessageExtend<>(body);
        message.setKey(root.getString("key"));
        if (headers != null && !headers.isEmpty()) {
            message.setHeaders(headers);
        }
        String uuid = root.getString("uuid");
        if (uuid != null) {
            message.setUuid(uuid);
        }
        Date producerTime = root.getDate("producerTime");
        if (producerTime != null) {
            message.setProducerTime(producerTime);
        }
        return message;
    }
}