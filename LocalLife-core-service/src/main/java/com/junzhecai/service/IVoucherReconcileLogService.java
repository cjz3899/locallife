package com.junzhecai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.junzhecai.dto.VoucherReconcileLogDto;
import com.junzhecai.entity.VoucherReconcileLog;

public interface IVoucherReconcileLogService extends IService<VoucherReconcileLog> {
    
    
    boolean saveReconcileLog(VoucherReconcileLogDto voucherReconcileLogDto);
}