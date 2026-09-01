package com.junzhecai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.core.RedisKeyManage;
import com.junzhecai.dto.Result;
import com.junzhecai.entity.UserInfo;
import com.junzhecai.enums.BaseCode;
import com.junzhecai.exception.LocalLifeFrameException;
import com.junzhecai.mapper.UserInfoMapper;
import com.junzhecai.redis.RedisCache;
import com.junzhecai.redis.RedisKeyBuild;
import com.junzhecai.service.IUserInfoService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {
    @Resource
    private RedisCache redisCache;

    @Override
    public UserInfo getByUserId(Long userId) {
        UserInfo userInfo = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.USER_INFO_KEY, userId), UserInfo.class);
        if (Objects.nonNull(userInfo)) {
            return userInfo;
        }
        userInfo = query().eq("user_id", userId).one();
        if (Objects.isNull(userInfo)) {
            throw new LocalLifeFrameException(BaseCode.USER_NOT_EXIST);
        }
        redisCache.set(RedisKeyBuild.createRedisKey(RedisKeyManage.USER_INFO_KEY, userId), userInfo);
        return userInfo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> updateUserLevel(Long userId, Integer newLevel) {
        return null;
    }

}
