package com.junzhecai.servicelock.factory;

import com.junzhecai.core.ManageLocker;
import com.junzhecai.servicelock.LockType;
import com.junzhecai.servicelock.ServiceLocker;
import lombok.AllArgsConstructor;


@AllArgsConstructor
public class ServiceLockFactory {

    private final ManageLocker manageLocker;


    public ServiceLocker getLock(LockType lockType) {
        ServiceLocker lock = switch (lockType) {
            case Fair -> manageLocker.getFairLocker();
            case Write -> manageLocker.getWriteLocker();
            case Read -> manageLocker.getReadLocker();
            default -> manageLocker.getReentrantLocker();
        };
        return lock;
    }
}
