package com.junzhecai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.junzhecai.dto.CancelVoucherOrderDto;
import com.junzhecai.dto.GetVoucherOrderByVoucherIdDto;
import com.junzhecai.dto.GetVoucherOrderDto;
import com.junzhecai.dto.Result;
import com.junzhecai.entity.VoucherOrder;
import com.junzhecai.kafka.message.SeckillVoucherMessage;
import com.junzhecai.message.MessageExtend;

public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result<Long> seckillVoucher(Long voucherId);

    Long getSeckillVoucherOrder(GetVoucherOrderDto getVoucherOrderDto);

    Boolean cancel(CancelVoucherOrderDto cancelVoucherOrderDto);

    boolean autoIssueVoucherToEarliestSubscriber(final Long voucherId, final Long excludeUserId);

    Long getSeckillVoucherOrderIdByVoucherId(GetVoucherOrderByVoucherIdDto getVoucherOrderByVoucherIdDto);

    boolean createVoucherOrder(MessageExtend<SeckillVoucherMessage> message);
}
