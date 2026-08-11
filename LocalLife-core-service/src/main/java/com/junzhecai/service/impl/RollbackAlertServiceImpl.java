package com.junzhecai.service.impl;

import lombok.extern.slf4j.Slf4j;
import com.junzhecai.entity.RollbackFailureLog;
import com.junzhecai.service.IRollbackAlertService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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



    @Override
    public void sendRollbackAlert(RollbackFailureLog logEntity) {
        
    }
}