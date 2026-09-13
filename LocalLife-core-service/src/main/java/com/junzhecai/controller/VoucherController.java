package com.junzhecai.controller;

import com.junzhecai.dto.*;
import com.junzhecai.model.SeckillVoucherFullModel;
import com.junzhecai.service.ISeckillVoucherService;
import com.junzhecai.service.IVoucherService;
import com.junzhecai.vo.GetSubscribeStatusVo;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/voucher")
public class VoucherController {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private IVoucherService voucherService;

    @GetMapping("/get")
    public Result<SeckillVoucherFullModel> get(@Valid @RequestBody GetSeckillVoucherDto getSeckillVoucherDto) {
        return Result.ok(seckillVoucherService.queryByVoucherId(getSeckillVoucherDto.getVoucherId()));
    }

    @PostMapping("/update/seckill")
    public Result<Void> updateSeckillVoucher(@Valid @RequestBody UpdateSeckillVoucherDto updateSeckillVoucherDto) {
        voucherService.updateSeckillVoucher(updateSeckillVoucherDto);
        return Result.ok();
    }

    @PostMapping("/update/seckill/stock")
    public Result<Void> updateSeckillVoucherStock(@Valid @RequestBody UpdateSeckillVoucherStockDto updateSeckillVoucherStockDto) {
        voucherService.updateSeckillVoucherStock(updateSeckillVoucherStockDto);
        return Result.ok();
    }

    @PostMapping("/subscribe")
    public Result<Void> subscribe(@Valid @RequestBody VoucherSubscribeDto voucherSubscribeDto) {
        voucherService.subscribe(voucherSubscribeDto);
        return Result.ok();
    }

    @PostMapping("/unsubscribe")
    public Result<Void> unsubscribe(@Valid @RequestBody VoucherSubscribeDto voucherSubscribeDto) {
        voucherService.unsubscribe(voucherSubscribeDto);
        return Result.ok();
    }

    @PostMapping("/get/subscribe/status")
    public Result<Integer> getSubscribeStatus(@Valid @RequestBody VoucherSubscribeDto voucherSubscribeDto) {
        return Result.ok(voucherService.getSubscribeStatus(voucherSubscribeDto));
    }

    @PostMapping("/get/subscribe/status/batch")
    public Result<List<GetSubscribeStatusVo>> getSubscribeStatusBatch(@Valid @RequestBody VoucherSubscribeBatchDto voucherSubscribeBatchDto) {
        return Result.ok(voucherService.getSubscribeStatusBatch(voucherSubscribeBatchDto));
    }
}
