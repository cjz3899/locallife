package com.junzhecai.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.dto.Result;
import com.junzhecai.dto.UserDTO;
import com.junzhecai.entity.Follow;
import com.junzhecai.mapper.FollowMapper;
import com.junzhecai.service.IFollowService;
import com.junzhecai.service.IUserService;
import com.junzhecai.toolkit.SnowflakeIdGenerator;
import com.junzhecai.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.junzhecai.utils.RedisConstants.FOLLOWS_KEY;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        String key = FOLLOWS_KEY + userId;
        if (isFollow) {
            //关注
            Follow follow = new Follow();
            follow.setId(snowflakeIdGenerator.nextId());
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            //取关
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Long count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        Long userId = UserHolder.getUser().getId();
        //当前用户id
        String key = FOLLOWS_KEY + userId;
        //目标用户id
        String key2 = FOLLOWS_KEY + id;
        //求交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        //解析id
        List<Long> ids = intersect.stream().map(Long::valueOf).toList();
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .toList();
        return Result.ok(users);
    }
}
