package com.junzhecai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.junzhecai.entity.SeckillVoucher;
import com.junzhecai.model.SeckillVoucherFullModel;

public interface ISeckillVoucherService extends IService<SeckillVoucher> {
    
    SeckillVoucherFullModel queryByVoucherId(Long voucherId);
    
    void loadVoucherStock(Long voucherId);
    
    boolean rollbackStock(Long voucherId);
}
