package com.junzhecai.controller;

import com.junzhecai.context.RateLimitScene;
import com.junzhecai.dto.Result;
import com.junzhecai.handler.RateLimitHandler;
import com.junzhecai.service.ISeckillAccessTokenService;
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
    @Resource
    private ISeckillAccessTokenService seckillAccessTokenService;

    @PostMapping("/seckill/{id}")
    public Result<Long> seckillVoucher(@PathVariable("id") Long voucherId,
                                       @RequestParam(name = "accessToken", required = false) String accessToken) {
        Long userId = UserHolder.getUser().getId();
        rateLimitHandler.execute(voucherId, userId, RateLimitScene.SECKILL_ORDER);
        if (seckillAccessTokenService.isEnabled()) {
            if (accessToken != null || !seckillAccessTokenService.validateAndConsume(voucherId, userId, null)) {
                return Result.fail("令牌检验失败或令牌已失效");
            }
        }
        return voucherOrderService.seckillVoucher(voucherId);
    }

    @GetMapping("/seckill/token/{id}")
    public Result<String> issueSeckillAccessToken(@PathVariable("id") Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        rateLimitHandler.execute(voucherId, userId, RateLimitScene.ISSUE_TOKEN);
        String token = seckillAccessTokenService.issueAccessToken(voucherId, userId);
        return Result.ok(token);
    }


}
