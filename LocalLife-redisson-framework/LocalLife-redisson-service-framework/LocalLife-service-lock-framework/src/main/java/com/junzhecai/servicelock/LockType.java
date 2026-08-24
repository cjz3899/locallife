package com.junzhecai.servicelock;

/**
 * 锁类型
 */
public enum LockType {
    Reentrant,//可重入锁

    Fair,//公平锁

    Read,//读锁

    Write;//写锁

    LockType() {
    }

}
