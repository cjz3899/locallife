package com.junzhecai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import com.junzhecai.dto.GetVoucherOrderRouterDto;
import com.junzhecai.entity.VoucherOrderRouter;
import com.junzhecai.mapper.VoucherOrderRouterMapper;
import com.junzhecai.service.IVoucherOrderRouterService;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class VoucherOrderRouterServiceImpl extends ServiceImpl<VoucherOrderRouterMapper, VoucherOrderRouter> implements IVoucherOrderRouterService {
    
    @Override
    public Long get(GetVoucherOrderRouterDto getVoucherOrderRouterDto) {
        return null;
    }
}
