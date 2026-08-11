package com.junzhecai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import com.junzhecai.dto.Result;
import com.junzhecai.entity.UserInfo;
import com.junzhecai.mapper.UserInfoMapper;
import com.junzhecai.service.IUserInfoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {
    
    
    @Override
    public UserInfo getByUserId(Long userId){
        return null;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> updateUserLevel(Long userId, Integer newLevel) {
        return null;
    }

}
