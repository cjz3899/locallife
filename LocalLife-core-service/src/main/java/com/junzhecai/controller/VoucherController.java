package com.junzhecai.controller;

import com.junzhecai.dto.GetSeckillVoucherDto;
import com.junzhecai.dto.Result;
import com.junzhecai.dto.UpdateSeckillVoucherDto;
import com.junzhecai.dto.UpdateSeckillVoucherStockDto;
import com.junzhecai.model.SeckillVoucherFullModel;
import com.junzhecai.service.ISeckillVoucherService;
import com.junzhecai.service.IVoucherService;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

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
}
