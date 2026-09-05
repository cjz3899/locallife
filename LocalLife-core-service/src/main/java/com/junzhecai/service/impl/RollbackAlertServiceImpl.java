package com.junzhecai.service.impl;

import cn.hutool.core.util.StrUtil;
import com.junzhecai.core.RedisKeyManage;
import com.junzhecai.entity.RollbackFailureLog;
import com.junzhecai.redis.RedisCache;
import com.junzhecai.redis.RedisKeyBuild;
import com.junzhecai.service.IRollbackAlertService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

//模拟短信、邮件告警服务，实际应用中需要接入相应的SDK
@Slf4j
@Service
public class RollbackAlertServiceImpl implements IRollbackAlertService {

    @Value("${seckill.rollback.alert.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${seckill.rollback.alert.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${seckill.rollback.alert.sms.to:}")
    private String smsTo;

    @Value("${seckill.rollback.alert.email.to:}")
    private String emailTo;

    @Value("${seckill.rollback.alert.dedup.window.seconds:300}")
    private long dedupWindowSeconds;

    @Resource
    private RedisCache redisCache;


    @Override
    public void sendRollbackAlert(RollbackFailureLog logEntity) {
        try {
            if (!shouldNotify(logEntity.getVoucherId())) {
                return;
            }
            String content = formatContent(logEntity);
            if (smsEnabled && StrUtil.isNotBlank(smsTo)) {
                log.warn("[ROLLBACK_SMS] to={} content={}", smsTo, content);
            }
            if (emailEnabled && StrUtil.isNotBlank(emailTo)) {
                log.warn("[ROLLBACK_EMAIL] to={} content={}", emailTo, content);
            }
        } catch (Exception e) {
            log.error("发送回滚告警失败异常", e);
        }

    }

    //redis中存在这个键就返回true，否则返回false
    private boolean shouldNotify(Long voucherId) {
        try {
            return redisCache.setIfAbsent(
                    RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_ROLLBACK_ALERT_DEDUP_KEY, voucherId),
                    "1",
                    dedupWindowSeconds,
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            return true;
        }
    }

    private String formatContent(RollbackFailureLog rollbackFailureLog) {
        String time =
                rollbackFailureLog.getCreateTime() == null ?
                        ""
                        :
                        rollbackFailureLog.getCreateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return String.format("回滚失败告警 | voucherId=%s userId=%s orderId=%s traceId=%s attempts=%s source=%s time=%s detail=%s",
                rollbackFailureLog.getVoucherId(),
                rollbackFailureLog.getUserId(),
                rollbackFailureLog.getOrderId(),
                rollbackFailureLog.getTraceId(),
                rollbackFailureLog.getRetryAttempts(),
                rollbackFailureLog.getSource(),
                time,
                rollbackFailureLog.getDetail());
    }
}