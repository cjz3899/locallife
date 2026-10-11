package com.junzhecai.controller;

import com.junzhecai.dto.LoginFormDTO;
import com.junzhecai.dto.Result;
import com.junzhecai.service.IUserService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @PostMapping("/code")
    public Result<String> sendCode(@RequestParam("phone") String phone) {
        return userService.sendCode(phone);
    }

    @PostMapping("/login")
    public Result<String> login(@RequestBody LoginFormDTO loginForm) {
        return userService.login(loginForm);
    }
}
