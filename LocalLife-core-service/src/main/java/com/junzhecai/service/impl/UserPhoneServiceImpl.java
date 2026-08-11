package com.junzhecai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import com.junzhecai.entity.UserPhone;
import com.junzhecai.mapper.UserPhoneMapper;
import com.junzhecai.service.IUserPhoneService;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UserPhoneServiceImpl extends ServiceImpl<UserPhoneMapper, UserPhone> implements IUserPhoneService {
    
}
