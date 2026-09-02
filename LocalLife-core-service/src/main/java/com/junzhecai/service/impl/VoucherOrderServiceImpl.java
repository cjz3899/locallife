package com.junzhecai.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.annotion.RepeatExecuteLimit;
import com.junzhecai.core.RedisKeyManage;
import com.junzhecai.core.SpringUtil;
import com.junzhecai.dto.CancelVoucherOrderDto;
import com.junzhecai.dto.GetVoucherOrderByVoucherIdDto;
import com.junzhecai.dto.GetVoucherOrderDto;
import com.junzhecai.dto.Result;
import com.junzhecai.entity.UserInfo;
import com.junzhecai.entity.VoucherOrder;
import com.junzhecai.entity.VoucherOrderRouter;
import com.junzhecai.enums.BaseCode;
import com.junzhecai.enums.BusinessType;
import com.junzhecai.enums.LogType;
import com.junzhecai.enums.OrderStatus;
import com.junzhecai.exception.LocalLifeFrameException;
import com.junzhecai.kafka.message.SeckillVoucherMessage;
import com.junzhecai.kafka.producer.SeckillVoucherProducer;
import com.junzhecai.lua.SeckillVoucherDomain;
import com.junzhecai.lua.SeckillVoucherOperate;
import com.junzhecai.mapper.VoucherOrderMapper;
import com.junzhecai.message.MessageExtend;
import com.junzhecai.model.SeckillVoucherFullModel;
import com.junzhecai.redis.RedisCache;
import com.junzhecai.redis.RedisKeyBuild;
import com.junzhecai.service.*;
import com.junzhecai.toolkit.SnowflakeIdGenerator;
import com.junzhecai.utils.UserHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.junzhecai.constant.Constant.SECKILL_VOUCHER_TOPIC;
import static com.junzhecai.constant.RepeatExecuteLimitConstants.SECKILL_VOUCHER_ORDER;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private IUserInfoService userInfoService;
    @Resource
    private SeckillVoucherOperate seckillVoucherOperate;
    @Resource
    private IVoucherOrderRouterService voucherOrderRouterService;
    @Resource
    private RedisCache redisCache;
    @Resource
    private IVoucherReconcileLogService voucherReconcileLogService;

    public static final ThreadPoolExecutor SECKILL_ORDER_EXECUTOR =
            new ThreadPoolExecutor(
                    1,
                    1,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>(1024),
                    new NamedThreadFactory("seckill-order-", false),
                    new ThreadPoolExecutor.CallerRunsPolicy()
            );
    @Resource
    private SnowflakeIdGenerator snowflakeIdGenerator;
    @Resource
    private SeckillVoucherProducer seckillVoucherProducer;

    private static class NamedThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final boolean daemon;
        private final AtomicInteger index = new AtomicInteger(1);

        public NamedThreadFactory(String namePrefix, boolean daemon) {
            this.namePrefix = namePrefix;
            this.daemon = daemon;
        }

        @Override
        public Thread newThread(@NonNull Runnable r) {
            Thread t = new Thread(r, namePrefix + index.getAndIncrement());
            t.setDaemon(daemon);
            t.setUncaughtExceptionHandler((thread, ex) ->
                    log.error("未捕获异常，线程={}, err={}", thread.getName(), ex.getMessage(), ex)
            );
            return t;
        }
    }

    @Override
    public Result<Long> seckillVoucher(Long voucherId) {
        //查询秒杀优惠券
        SeckillVoucherFullModel seckillVoucherFullModel = seckillVoucherService.queryByVoucherId(voucherId);
        //加载优惠券库存
        seckillVoucherService.loadVoucherStock(voucherId);
        Long userId = UserHolder.getUser().getId();
        //验证会员等级
        verifyUserLevel(seckillVoucherFullModel, userId);
        long orderId = snowflakeIdGenerator.nextId();
        long traceId = snowflakeIdGenerator.nextId();
        //执行lua脚本需要的key
        List<String> keys = ListUtil.of(
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_STOCK_TAG_KEY, voucherId).getRelKey(),
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_USER_TAG_KEY, voucherId).getRelKey(),
                RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_TRACE_LOG_TAG_KEY, voucherId).getRelKey()
        );
        //执行lua脚本需要的参数
        String[] args = new String[9];
        args[0] = voucherId.toString();
        args[1] = userId.toString();
        args[2] = String.valueOf(LocalDateTimeUtil.toEpochMilli(seckillVoucherFullModel.getBeginTime()));
        args[3] = String.valueOf(LocalDateTimeUtil.toEpochMilli(seckillVoucherFullModel.getEndTime()));
        args[4] = String.valueOf(seckillVoucherFullModel.getStatus());
        args[5] = String.valueOf(orderId);
        args[6] = String.valueOf(traceId);
        args[7] = String.valueOf(LogType.DEDUCT.getCode());
        long seconds = Duration.between(LocalDateTimeUtil.now(), seckillVoucherFullModel.getEndTime()).getSeconds();
        long ttlSeconds = Math.max(1L, seconds + Duration.ofDays(1).getSeconds());
        args[8] = String.valueOf(ttlSeconds);
        SeckillVoucherDomain seckillVoucherDomain = seckillVoucherOperate.execute(keys, args);
        if (!seckillVoucherDomain.getCode().equals(BaseCode.SUCCESS.getCode())) {
            throw new LocalLifeFrameException(Objects.requireNonNull(BaseCode.getRc(seckillVoucherDomain.getCode())));
        }
        SeckillVoucherMessage seckillVoucherMessage = new SeckillVoucherMessage(
                userId,
                voucherId,
                orderId,
                traceId,
                seckillVoucherDomain.getBeforeQty(),
                seckillVoucherDomain.getDeductQty(),
                seckillVoucherDomain.getAfterQty(),
                Boolean.FALSE
        );
        seckillVoucherProducer.sendPayload(SpringUtil.getPrefixDistinctionName() + "-" + SECKILL_VOUCHER_TOPIC, seckillVoucherMessage);
        return Result.ok(orderId);
    }

    private void verifyUserLevel(SeckillVoucherFullModel seckillVoucherFullModel, Long userId) {
        String allowedLevelsStr = seckillVoucherFullModel.getAllowedLevels();
        Integer minLevel = seckillVoucherFullModel.getMinLevel();
        boolean hasLevelRule = (StrUtil.isNotBlank(allowedLevelsStr)) || Objects.nonNull(minLevel);
        //如果没有设置用户等级规则，则直接返回
        if (!hasLevelRule) {
            return;
        }
        UserInfo userInfo = userInfoService.getByUserId(userId);
        boolean allowed = true;
        Integer level = userInfo.getLevel();
        if (StrUtil.isNotBlank(allowedLevelsStr)) {
            try {
                Set<Integer> allowedLevels = Arrays.stream(allowedLevelsStr.split(","))
                        .map(String::trim)
                        .filter(StrUtil::isNotBlank)
                        .map(Integer::valueOf)
                        .collect(Collectors.toSet());
                if (CollectionUtil.isNotEmpty(allowedLevels)) {
                    allowed = allowedLevels.contains(level);
                }
            } catch (Exception e) {
                log.warn("allowedLevels 解析失败", e);
            }
        }
        if (allowed && Objects.nonNull(minLevel)) {
            allowed = Objects.nonNull(level) && level >= minLevel;
        }
        if (!allowed) {
            throw new LocalLifeFrameException("当前会员级别不满足条件");
        }
    }

    @Override
    public Long getSeckillVoucherOrder(GetVoucherOrderDto getVoucherOrderDto) {
        return null;
    }

    @Override
    public Long getSeckillVoucherOrderIdByVoucherId(GetVoucherOrderByVoucherIdDto getVoucherOrderByVoucherIdDto) {
        return null;
    }

    @Override
    @RepeatExecuteLimit(name = SECKILL_VOUCHER_ORDER, keys = {"#message.uuid"})
    @Transactional(rollbackFor = Exception.class)
    public boolean createVoucherOrder(MessageExtend<SeckillVoucherMessage> message) {
        //获取消息体
        SeckillVoucherMessage messageBody = message.getMessageBody();
        Long userId = messageBody.getUserId();
        //根据优惠券id和用户id查询是否已经存在正常订单
        VoucherOrder normalVoucherOrder = query()
                /*分片键字段：voucher_id user_id
                 * 查询时要同时作为查询条件，确保分片键的唯一性*/
                .eq("voucher_id", messageBody.getVoucherId())
                .eq("user_id", userId)
                .eq("status", OrderStatus.NORMAL.getCode()).one();
        if (Objects.nonNull(normalVoucherOrder)) {
            log.warn("用户{}已经存在正常订单，优惠券{}", userId, messageBody.getVoucherId());
        }
        //扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", messageBody.getVoucherId())
                .gt("stock", 0).update();
        if (!success) {
            throw new LocalLifeFrameException("优惠券库存不足，优惠券id:" + messageBody.getVoucherId());
        }
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(messageBody.getOrderId());
        voucherOrder.setUserId(messageBody.getUserId());
        voucherOrder.setVoucherId(messageBody.getVoucherId());
        voucherOrder.setCreateTime(LocalDateTimeUtil.now());
        save(voucherOrder);
        //创建订单路由
        VoucherOrderRouter voucherOrderRouter = new VoucherOrderRouter();
        voucherOrderRouter.setId(snowflakeIdGenerator.nextId());
        voucherOrderRouter.setOrderId(voucherOrder.getId());
        voucherOrderRouter.setUserId(userId);
        voucherOrderRouter.setVoucherId(voucherOrder.getVoucherId());
        voucherOrderRouter.setCreateTime(LocalDateTimeUtil.now());
        voucherOrderRouter.setUpdateTime(LocalDateTimeUtil.now());
        voucherOrderRouterService.save(voucherOrderRouter);
        redisCache.set(RedisKeyBuild.createRedisKey(RedisKeyManage.DB_SECKILL_ORDER_KEY,
                        messageBody.getOrderId()),
                voucherOrder, 60,
                TimeUnit.SECONDS);
        //对账日志
        voucherReconcileLogService.saveReconcileLog(
                LogType.DEDUCT.getCode(),
                BusinessType.SUCCESS.getCode(),
                "order created",
                message
        );
        return true;
    }

    @Override
    public Boolean cancel(CancelVoucherOrderDto cancelVoucherOrderDto) {
        return false;
    }

    @Override
    public boolean autoIssueVoucherToEarliestSubscriber(final Long voucherId, final Long excludeUserId) {
        return false;
    }
}
