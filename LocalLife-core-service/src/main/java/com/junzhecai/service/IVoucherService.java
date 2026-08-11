package com.junzhecai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.junzhecai.dto.DelayVoucherReminderDto;
import com.junzhecai.dto.Result;
import com.junzhecai.dto.SeckillVoucherDto;
import com.junzhecai.dto.UpdateSeckillVoucherDto;
import com.junzhecai.dto.UpdateSeckillVoucherStockDto;
import com.junzhecai.dto.VoucherDto;
import com.junzhecai.dto.VoucherSubscribeBatchDto;
import com.junzhecai.dto.VoucherSubscribeDto;
import com.junzhecai.entity.Voucher;
import com.junzhecai.vo.GetSubscribeStatusVo;

import java.util.List;

public interface IVoucherService extends IService<Voucher> {

    Long addVoucher(VoucherDto voucherDto);
    
    Result<List<Voucher>> queryVoucherOfShop(Long shopId);

    Long addSeckillVoucher(SeckillVoucherDto seckillVoucherDto);
    
    void updateSeckillVoucher(UpdateSeckillVoucherDto updateSeckillVoucherDto);
    
    void updateSeckillVoucherStock(UpdateSeckillVoucherStockDto updateSeckillVoucherDto);
    
    void subscribe(VoucherSubscribeDto voucherSubscribeDto);
    
    void unsubscribe(VoucherSubscribeDto voucherSubscribeDto);
    
    Integer getSubscribeStatus(VoucherSubscribeDto voucherSubscribeDto);
    
    List<GetSubscribeStatusVo> getSubscribeStatusBatch(VoucherSubscribeBatchDto voucherSubscribeBatchDto);
    
    void delayVoucherReminder(DelayVoucherReminderDto delayVoucherReminderDto);
}
