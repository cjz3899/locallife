package com.junzhecai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.junzhecai.dto.VoucherReconcileLogDto;
import com.junzhecai.entity.VoucherReconcileLog;
import com.junzhecai.kafka.message.SeckillVoucherMessage;
import com.junzhecai.message.MessageExtend;

public interface IVoucherReconcileLogService extends IService<VoucherReconcileLog> {

    boolean saveReconcileLog(Integer logType, Integer businessType, String detail, MessageExtend<SeckillVoucherMessage> message);

    boolean saveReconcileLog(VoucherReconcileLogDto voucherReconcileLogDto);

    boolean saveReconcileLog(Integer logType, Integer businessType, String detail, long traceId, MessageExtend<SeckillVoucherMessage> message);
}