package com.junzhecai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.dto.VoucherReconcileLogDto;
import com.junzhecai.entity.VoucherReconcileLog;
import com.junzhecai.enums.LogType;
import com.junzhecai.kafka.message.SeckillVoucherMessage;
import com.junzhecai.mapper.VoucherReconcileLogMapper;
import com.junzhecai.message.MessageExtend;
import com.junzhecai.service.IVoucherReconcileLogService;
import com.junzhecai.toolkit.SnowflakeIdGenerator;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;


@Service
public class VoucherReconcileLogServiceImpl extends ServiceImpl<VoucherReconcileLogMapper, VoucherReconcileLog>
        implements IVoucherReconcileLogService {
    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean saveReconcileLog(Integer logType, Integer businessType, String detail, MessageExtend<SeckillVoucherMessage> message) {
        return saveReconcileLog(buildReconcileLogDto(logType, businessType, detail,
                message.getMessageBody().getTraceId(), message));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean saveReconcileLog(VoucherReconcileLogDto voucherReconcileLogDto) {
        return save(toEntity(voucherReconcileLogDto));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean saveReconcileLog(Integer logType, Integer businessType, String detail, long traceId, MessageExtend<SeckillVoucherMessage> message) {
        return saveReconcileLog(buildReconcileLogDto(logType, businessType, detail, traceId, message));
    }

    private VoucherReconcileLogDto buildReconcileLogDto(Integer logType, Integer businessType, String detail,
                                                        Long traceId, MessageExtend<SeckillVoucherMessage> message) {
        SeckillVoucherMessage messageBody = message.getMessageBody();
        VoucherReconcileLogDto voucherReconcileLogDto = VoucherReconcileLogDto.builder()
                .orderId(messageBody.getOrderId())
                .userId(messageBody.getUserId())
                .voucherId(messageBody.getVoucherId())
                .messageId(message.getUuid())
                .detail(detail)
                .traceId(traceId)
                .logType(logType)
                .businessType(businessType)
                .changeQty(messageBody.getChangeQty()).build();
        if (LogType.RESTORE.getCode().equals(logType)) {
            voucherReconcileLogDto.setBeforeQty(messageBody.getAfterQty());
            voucherReconcileLogDto.setAfterQty(messageBody.getBeforeQty());
        } else {
            voucherReconcileLogDto.setBeforeQty(messageBody.getBeforeQty());
            voucherReconcileLogDto.setAfterQty(messageBody.getAfterQty());
        }
        return voucherReconcileLogDto;
    }

    private VoucherReconcileLog toEntity(VoucherReconcileLogDto voucherReconcileLogDto) {
        VoucherReconcileLog voucherReconcileLog = new VoucherReconcileLog();
        BeanUtils.copyProperties(voucherReconcileLogDto, voucherReconcileLog);
        LocalDateTime now = LocalDateTime.now();
        return voucherReconcileLog.setId(snowflakeIdGenerator.nextId())
                .setCreateTime(now)
                .setUpdateTime(now);
    }
}
