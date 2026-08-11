package com.junzhecai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import com.junzhecai.entity.SeckillVoucher;
import com.junzhecai.mapper.SeckillVoucherMapper;
import com.junzhecai.model.SeckillVoucherFullModel;
import com.junzhecai.service.ISeckillVoucherService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {
    
    
    @Override
    public SeckillVoucherFullModel queryByVoucherId(Long voucherId) {
        return null;
    }
    
    @Override
    public void loadVoucherStock(Long voucherId){
       
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackStock(final Long voucherId) {
        return false;
    }
}
