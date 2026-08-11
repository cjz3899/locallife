package com.junzhecai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.dto.VoucherReconcileLogDto;
import com.junzhecai.entity.VoucherReconcileLog;
import com.junzhecai.mapper.VoucherReconcileLogMapper;
import com.junzhecai.service.IVoucherReconcileLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoucherReconcileLogServiceImpl extends ServiceImpl<VoucherReconcileLogMapper, VoucherReconcileLog>
        implements IVoucherReconcileLogService {
    
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveReconcileLog(VoucherReconcileLogDto voucherReconcileLogDto) {
        return false;
    }
}