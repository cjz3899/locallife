package com.junzhecai.lockinfo;

import org.aspectj.lang.JoinPoint;

public interface LockInfoHandle {

    String getLockName(JoinPoint joinPoint, String name, String[] keys);

    String simpleGetLockName(String name, String[] keys);
}
