package com.junzhecai.lockinfo.impl;

import com.junzhecai.lockinfo.AbstractLockInfoHandle;

public class RepeatExecuteLimitLockInfoHandle extends AbstractLockInfoHandle {
    private static final String PREFIX_NAME = "REPEAT_EXECUTE_LIMIT";

    @Override
    protected String getLockPrefixName() {
        return PREFIX_NAME;
    }
}
