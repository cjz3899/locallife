package com.junzhecai.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.dto.*;
import com.junzhecai.entity.Voucher;
import com.junzhecai.mapper.VoucherMapper;
import com.junzhecai.service.IVoucherService;
import com.junzhecai.vo.GetSubscribeStatusVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {
    @Value("${seckill.reminder.ahead.seconds:120}")
    private long reminderAheadSeconds;

    @Override
    public Long addVoucher(VoucherDto voucherDto) {
        return null;
    }

    @Override
    public Result<List<Voucher>> queryVoucherOfShop(Long shopId) {
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addSeckillVoucher(SeckillVoucherDto seckillVoucherDto) {
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSeckillVoucher(UpdateSeckillVoucherDto updateSeckillVoucherDto) {
        Long voucherId = updateSeckillVoucherDto.getVoucherId();
        //更新tb_voucher表的非空字段
        boolean updatedVoucher = false;
        UpdateWrapper<Voucher> voucherUpdate = new UpdateWrapper<Voucher>().eq("id", voucherId);
        if (updateSeckillVoucherDto.getTitle() != null) {
            voucherUpdate.set("title", updateSeckillVoucherDto.getTitle());
            updatedVoucher = true;
        }
        

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSeckillVoucherStock(UpdateSeckillVoucherStockDto updateSeckillVoucherDto) {

    }

    @Override
    public void subscribe(final VoucherSubscribeDto voucherSubscribeDto) {

    }

    @Override
    public void unsubscribe(final VoucherSubscribeDto voucherSubscribeDto) {

    }

    @Override
    public Integer getSubscribeStatus(final VoucherSubscribeDto voucherSubscribeDto) {
        return null;
    }

    @Override
    public List<GetSubscribeStatusVo> getSubscribeStatusBatch(final VoucherSubscribeBatchDto voucherSubscribeBatchDto) {
        return null;
    }


    @Override
    public void delayVoucherReminder(DelayVoucherReminderDto delayVoucherReminderDto) {

    }
}
