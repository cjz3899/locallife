package com.junzhecai.service.impl;

import lombok.extern.slf4j.Slf4j;
import com.junzhecai.service.IAutoIssueNotifyService;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AutoIssueNotifyServiceImpl implements IAutoIssueNotifyService {

    

    @Override
    public void sendAutoIssueNotify(Long voucherId, Long userId, Long orderId) {
        
    }
}