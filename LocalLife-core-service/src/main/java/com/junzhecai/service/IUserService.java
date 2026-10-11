package com.junzhecai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.junzhecai.dto.LoginFormDTO;
import com.junzhecai.dto.Result;
import com.junzhecai.entity.User;


public interface IUserService extends IService<User> {

    Result<String> sendCode(String phone);

    Result<String> login(LoginFormDTO loginForm);

    Result<Void> sign();

    Result<Integer> signCount();

}
