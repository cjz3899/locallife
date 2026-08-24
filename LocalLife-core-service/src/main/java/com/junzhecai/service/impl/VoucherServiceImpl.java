package com.junzhecai.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.cache.SeckillVoucherCacheInvalidationPublisher;
import com.junzhecai.dto.*;
import com.junzhecai.entity.SeckillVoucher;
import com.junzhecai.entity.Voucher;
import com.junzhecai.mapper.VoucherMapper;
import com.junzhecai.service.ISeckillVoucherService;
import com.junzhecai.service.IVoucherService;
import com.junzhecai.vo.GetSubscribeStatusVo;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements IVoucherService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private SeckillVoucherCacheInvalidationPublisher seckillVoucherCacheInvalidationPublisher;

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
        UpdateWrapper<Voucher> voucherWrapper = new UpdateWrapper<Voucher>().eq("id", voucherId);
        for (Map.Entry<String, Function<UpdateSeckillVoucherDto, Object>> entry : VOUCHER_FIELD_MAPPING.entrySet()) {
            Object value = entry.getValue().apply(updateSeckillVoucherDto);
            if (value != null) {
                voucherWrapper.set(entry.getKey(), value);
                updatedVoucher = true;
            }
        }
        if (updatedVoucher) {
            voucherWrapper.set("update_time", LocalDateTimeUtil.now());
            update(voucherWrapper);
        }

        //更新 tb_seckill_voucher 表的非空字段（仅时间相关）
        boolean updatedSeckill = false;
        UpdateWrapper<SeckillVoucher> seckillWrapper = new UpdateWrapper<SeckillVoucher>().eq("voucher_id", voucherId);
        for (Map.Entry<String, Function<UpdateSeckillVoucherDto, Object>> entry : SECKILL_VOUCHER_FIELD_MAPPING.entrySet()) {
            Object value = entry.getValue().apply(updateSeckillVoucherDto);
            if (value != null) {
                seckillWrapper.set(entry.getKey(), value);
                updatedSeckill = true;
            }
        }
        if (updatedSeckill) {
            seckillWrapper.set("update_time", LocalDateTimeUtil.now());
            seckillVoucherService.update(seckillWrapper);
        }
        //更新后清理缓存，等待读路径按新数据重建缓存
        if (updatedVoucher || updatedSeckill) {
            seckillVoucherCacheInvalidationPublisher.publishInvalidate(voucherId, "update");
        }
    }

    //字段映射
    private static final Map<String, Function<UpdateSeckillVoucherDto, Object>> VOUCHER_FIELD_MAPPING = Map.of(
            "title", UpdateSeckillVoucherDto::getTitle,
            "sub_title", UpdateSeckillVoucherDto::getSubTitle,
            "rules", UpdateSeckillVoucherDto::getRules,
            "pay_value", UpdateSeckillVoucherDto::getPayValue,
            "actual_value", UpdateSeckillVoucherDto::getActualValue,
            "type", UpdateSeckillVoucherDto::getType,
            "status", UpdateSeckillVoucherDto::getStatus
    );

    private static final Map<String, Function<UpdateSeckillVoucherDto, Object>> SECKILL_VOUCHER_FIELD_MAPPING = Map.of(
            "begin_time", UpdateSeckillVoucherDto::getBeginTime,
            "end_time", UpdateSeckillVoucherDto::getEndTime,
            "allowed_levels", UpdateSeckillVoucherDto::getAllowedLevels,
            "min_level", UpdateSeckillVoucherDto::getMinLevel
    );

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
