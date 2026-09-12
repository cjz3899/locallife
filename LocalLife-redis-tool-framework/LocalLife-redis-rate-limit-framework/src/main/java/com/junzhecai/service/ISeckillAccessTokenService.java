package com.junzhecai.service;


public interface ISeckillAccessTokenService {
    /**
     * 是否启用访问令牌校验
     */
    boolean isEnabled();

    /**
     * 为用户申请指定voucher的访问令牌
     */
    String issueAccessToken(Long voucherId, Long userId);

    /**
     * 校验并消费令牌
     */
    boolean validateAndConsume(Long voucherId, Long userId, String token);
}
