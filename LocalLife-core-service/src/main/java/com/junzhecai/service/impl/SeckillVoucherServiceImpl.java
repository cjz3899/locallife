package com.junzhecai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.core.RedisKeyManage;
import com.junzhecai.entity.SeckillVoucher;
import com.junzhecai.mapper.SeckillVoucherMapper;
import com.junzhecai.model.SeckillVoucherFullModel;
import com.junzhecai.redis.RedisCache;
import com.junzhecai.redis.RedisKeyBuild;
import com.junzhecai.service.ISeckillVoucherService;
import com.junzhecai.utils.ServiceLockTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Slf4j
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private ServiceLockTool serviceLockTool;

    @Override

    public SeckillVoucherFullModel queryByVoucherId(Long voucherId) {
        //双重检测解决缓存击穿
        SeckillVoucherFullModel seckillVoucherFullModel = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_VOUCHER_TAG_KEY, voucherId),
                SeckillVoucherFullModel.class);
        if (Objects.nonNull(seckillVoucherFullModel)) {
            return seckillVoucherFullModel;
        }
        log.info("查询秒杀优惠券 从Redis缓存没有查询到 秒杀优惠券的优惠券id : {}", voucherId);
        //实现双重检测
        //加锁，解决缓存击穿

    }

    @Override
    public void loadVoucherStock(Long voucherId) {

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackStock(final Long voucherId) {
        return false;
    }
}
