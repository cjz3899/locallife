package com.junzhecai.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.dto.LoginFormDTO;
import com.junzhecai.dto.Result;
import com.junzhecai.dto.UserDTO;
import com.junzhecai.entity.User;
import com.junzhecai.entity.UserInfo;
import com.junzhecai.entity.UserPhone;
import com.junzhecai.mapper.UserMapper;
import com.junzhecai.service.IUserInfoService;
import com.junzhecai.service.IUserPhoneService;
import com.junzhecai.service.IUserService;
import com.junzhecai.toolkit.SnowflakeIdGenerator;
import com.junzhecai.utils.RegexUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.junzhecai.utils.RedisConstants.*;
import static com.junzhecai.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Resource
    private IUserInfoService userInfoService;

    @Resource
    private IUserPhoneService userPhoneService;

    @Override
    public Result<String> sendCode(String phone, HttpSession session) {
        //校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        String code = RandomUtil.randomNumbers(6);
        //将生成的验证码保存到session中
        session.setAttribute("code", code);
        log.debug("发送短信验证码成功，验证码：{}", code);
        return Result.ok();
    }

    // TODO 为什么要加事务
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result<String> login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }
        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = createUserWithPhone(phone);
        }
        String token = UUID.randomUUID().toString();
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) ->
                                fieldValue == null ? null : fieldValue.toString()));
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setId(snowflakeIdGenerator.nextId());
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        UserInfo userInfo = new UserInfo();
        userInfo.setId(snowflakeIdGenerator.nextId());
        userInfo.setUserId(user.getId());
        userInfo.setLevel(1);
        userInfoService.save(userInfo);
        try {
            maintainLevelSetMembership(user.getId());
        } catch (Exception e) {
            // 忽略异常，避免影响注册逻辑
        }
        // 4.保存用户手机信息
        UserPhone userPhone = new UserPhone();
        userPhone.setId(snowflakeIdGenerator.nextId());
        userPhone.setUserId(user.getId());
        userPhone.setPhone(phone);
        userPhoneService.save(userPhone);
        return user;
    }

    private void maintainLevelSetMembership(Long id) {
        return;
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
