package com.junzhecai.servicelock.info;


import com.junzhecai.exception.LocalLifeFrameException;

//锁超时策略
public enum LockTimeOutStrategy implements LockTimeOutHandler {
    /**
     * 快速失败
     *
     */
    FAIL() {
        @Override
        public void handler(String lockName) {
            String msg = String.format("%s请求频繁", lockName);
            throw new LocalLifeFrameException(msg);
        }
    }
}
