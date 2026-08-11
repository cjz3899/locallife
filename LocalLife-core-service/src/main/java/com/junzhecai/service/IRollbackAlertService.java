package com.junzhecai.service;

import com.junzhecai.entity.RollbackFailureLog;

public interface IRollbackAlertService {

    void sendRollbackAlert(RollbackFailureLog log);
}