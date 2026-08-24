package com.junzhecai.servicelock.factory;

import com.junzhecai.core.ManageLocker;
import com.junzhecai.servicelock.LockType;
import com.junzhecai.servicelock.ServiceLocker;
import lombok.AllArgsConstructor;


@AllArgsConstructor
public class ServiceLockFactory {

    private final ManageLocker manageLocker;


    public ServiceLocker getLock(LockType lockType) {
        ServiceLocker lock;
        switch (lockType) {
            case Fair:
                lock = manageLocker.getFairLocker();
                break;
            case Write:
                lock = manageLocker.getWriteLocker();
                break;
            case Read:
                lock = manageLocker.getReadLocker();
                break;
            default:
                lock = manageLocker.getReentrantLocker();
                break;
        }
        return lock;
    }
}
