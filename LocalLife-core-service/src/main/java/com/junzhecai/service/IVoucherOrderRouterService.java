package com.junzhecai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.junzhecai.dto.GetVoucherOrderRouterDto;
import com.junzhecai.entity.VoucherOrderRouter;

public interface IVoucherOrderRouterService extends IService<VoucherOrderRouter> {
    
    Long get(GetVoucherOrderRouterDto getVoucherOrderRouterDto);
}
