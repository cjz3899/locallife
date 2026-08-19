package com.junzhecai.controller;

import com.junzhecai.dto.GetSeckillVoucherDto;
import com.junzhecai.dto.Result;
import com.junzhecai.model.SeckillVoucherFullModel;
import com.junzhecai.service.ISeckillVoucherService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/voucher")
public class VoucherController {
    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @GetMapping("/get")
    public Result<SeckillVoucherFullModel> get(@Valid @RequestBody GetSeckillVoucherDto getSeckillVoucherDto) {
        return Result.ok(seckillVoucherService.queryByVoucherId(getSeckillVoucherDto.getVoucherId()));
    }
}
