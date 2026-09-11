package com.junzhecai.controller;

import com.junzhecai.context.RateLimitScene;
import com.junzhecai.dto.Result;
import com.junzhecai.handler.RateLimitHandler;
import com.junzhecai.service.IVoucherOrderService;
import com.junzhecai.utils.UserHolder;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {
    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private RateLimitHandler rateLimitHandler;

    @PostMapping("/seckill/{id}")
    public Result<Long> seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }

    @GetMapping("/seckill/token/{id}")
    public Result<String> issueSeckillAccessToken(@PathVariable("id") Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        rateLimitHandler.execute(voucherId, userId, RateLimitScene.ISSUE_TOKEN);
        //申请令牌
        return Result.ok("令牌申请成功");
    }
}
