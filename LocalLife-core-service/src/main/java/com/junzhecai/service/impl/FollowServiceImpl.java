package com.junzhecai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.dto.Result;
import com.junzhecai.entity.Follow;
import com.junzhecai.mapper.FollowMapper;
import com.junzhecai.service.IFollowService;
import org.springframework.stereotype.Service;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        return null;
    }

    @Override
    public Result isFollow(Long followUserId) {
        return null;
    }

    @Override
    public Result followCommons(Long id) {
        return null;
    }
}
