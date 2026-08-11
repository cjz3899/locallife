package com.junzhecai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import com.junzhecai.dto.LoginFormDTO;
import com.junzhecai.dto.Result;
import com.junzhecai.entity.User;
import com.junzhecai.mapper.UserMapper;
import com.junzhecai.service.IUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    

    @Override
    public Result<String> sendCode(String phone, HttpSession session) {
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> login(LoginFormDTO loginForm, HttpSession session) {
        return null;
    }

    @Override
    public Result<Void> sign() {
        return null;
    }

    @Override
    public Result<Integer> signCount() {
        return null;
    }
}
