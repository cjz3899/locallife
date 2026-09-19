package com.junzhecai.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.annotion.RepeatExecuteLimit;
import com.junzhecai.core.RedisKeyManage;
import com.junzhecai.core.SpringUtil;
import com.junzhecai.dto.*;
import com.junzhecai.entity.*;
import com.junzhecai.enums.*;
import com.junzhecai.exception.LocalLifeFrameException;
import com.junzhecai.kafka.message.SeckillVoucherMessage;
import com.junzhecai.kafka.producer.SeckillVoucherProducer;
import com.junzhecai.kafka.redis.RedisVoucherData;
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
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
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
    @Resource
    private IVoucherService voucherService;

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
    @Resource
    private RedisVoucherData redisVoucherData;

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

    /**
     * 验证用户等级
     */
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
        VoucherOrder voucherOrder = redisCache.get(RedisKeyBuild.createRedisKey(RedisKeyManage.DB_SECKILL_ORDER_KEY, getVoucherOrderDto.getOrderId()), VoucherOrder.class);
        if (Objects.nonNull(voucherOrder)) {
            return voucherOrder.getId();
        }
        VoucherOrderRouter one = voucherOrderRouterService.lambdaQuery().eq(VoucherOrderRouter::getOrderId, getVoucherOrderDto.getOrderId()).one();
        if (Objects.nonNull(one)) {
            return one.getOrderId();
        }
        return null;
    }

    @Override
    public Long getSeckillVoucherOrderIdByVoucherId(GetVoucherOrderByVoucherIdDto getVoucherOrderByVoucherIdDto) {
        VoucherOrder voucherOrder = lambdaQuery()
                .eq(VoucherOrder::getUserId, UserHolder.getUser().getId())
                .eq(VoucherOrder::getVoucherId, getVoucherOrderByVoucherIdDto.getVoucherId())
                .eq(VoucherOrder::getStatus, OrderStatus.NORMAL.getCode())
                .one();
        if (Objects.nonNull(voucherOrder)) {
            return voucherOrder.getId();
        }
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
        VoucherOrder voucherOrder = VoucherOrder.builder()
                .id(messageBody.getOrderId())
                .userId(messageBody.getUserId())
                .voucherId(messageBody.getVoucherId())
                .createTime(LocalDateTimeUtil.now())
                .build();
        save(voucherOrder);
        //创建订单路由
        VoucherOrderRouter voucherOrderRouter = VoucherOrderRouter.builder()
                .id(snowflakeIdGenerator.nextId())
                .orderId(voucherOrder.getId())
                .userId(userId)
                .voucherId(voucherOrder.getVoucherId())
                .createTime(LocalDateTimeUtil.now())
                .updateTime(LocalDateTimeUtil.now())
                .build();
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
    @Transactional(rollbackFor = Exception.class)
    public Boolean cancel(CancelVoucherOrderDto cancelVoucherOrderDto) {
        VoucherOrder voucherOrder = lambdaQuery()
                .eq(VoucherOrder::getUserId, UserHolder.getUser().getId())
                .eq(VoucherOrder::getVoucherId, cancelVoucherOrderDto.getVoucherId())
                .eq(VoucherOrder::getStatus, OrderStatus.NORMAL.getCode())
                .one();
        if (Objects.isNull(voucherOrder)) {
            throw new LocalLifeFrameException(BaseCode.SECKILL_VOUCHER_ORDER_NOT_EXIST);
        }
        SeckillVoucher seckillVoucher = seckillVoucherService.lambdaQuery()
                .eq(SeckillVoucher::getVoucherId, cancelVoucherOrderDto.getVoucherId())
                .one();
        if (Objects.isNull(seckillVoucher)) {
            throw new LocalLifeFrameException(BaseCode.SECKILL_VOUCHER_NOT_EXIST);
        }
        boolean updateResult = lambdaUpdate().set(VoucherOrder::getStatus, OrderStatus.CANCEL.getCode())
                .set(VoucherOrder::getUpdateTime, LocalDateTimeUtil.now())
                .eq(VoucherOrder::getUserId, UserHolder.getUser().getId())
                .eq(VoucherOrder::getVoucherId, cancelVoucherOrderDto.getVoucherId())
                .update();
        //对账日志
        long traceId = snowflakeIdGenerator.nextId();
        VoucherReconcileLogDto voucherReconcileLogDto = VoucherReconcileLogDto.builder()
                .orderId(voucherOrder.getId())
                .userId(voucherOrder.getUserId())
                .voucherId(voucherOrder.getVoucherId())
                .detail("cancel voucher order ")
                .beforeQty(seckillVoucher.getStock())
                .changeQty(1)
                .afterQty(seckillVoucher.getStock() + 1)
                .traceId(traceId)
                .logType(LogType.RESTORE.getCode())
                .businessType(BusinessType.CANCEL.getCode())
                .build();
        boolean saveReconcileLogResult = voucherReconcileLogService.saveReconcileLog(voucherReconcileLogDto);

        //恢复库存
        boolean rollbackStockResult = seckillVoucherService.rollbackStock(cancelVoucherOrderDto.getVoucherId());
        boolean result = updateResult && saveReconcileLogResult && rollbackStockResult;
        if (result) {
            redisVoucherData.rollbackRedisVoucherData(
                    SeckillVoucherOrderOperate.YES,
                    traceId,
                    voucherOrder.getVoucherId(),
                    voucherOrder.getUserId(),
                    voucherOrder.getId(),
                    seckillVoucher.getStock(),
                    1,
                    seckillVoucher.getStock() + 1
            );
            //移出订阅队列
            redisCache.delForHash(
                    RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_STATUS_TAG_KEY,
                            cancelVoucherOrderDto.getVoucherId()),
                    String.valueOf(voucherOrder.getUserId())
            );
            //在每日买家TopN统计中减1
            Voucher voucher = voucherService.getById(voucherOrder.getVoucherId());
            if (Objects.nonNull(voucher)) {
                String day = voucherOrder.getCreateTime().format(DateTimeFormatter.BASIC_ISO_DATE);
                RedisKeyBuild dailyKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SHOP_TOP_BUYERS_DAILY_TAG_KEY, voucher.getShopId(), day);
                redisCache.incrementScoreForSortedSet(dailyKey, String.valueOf(voucherOrder.getUserId()), -1.0);
            }
            //回滚成功后，自动分配给订阅队列中最早的用户
            try {
                autoIssueVoucherToEarliestSubscriber(voucherOrder.getVoucherId(), voucherOrder.getUserId());
            } catch (Exception e) {
                log.error("自动发券失败，voucherId={}", voucherOrder.getVoucherId(), e);
            }
        }
        return result;
    }


    @Override
    public boolean autoIssueVoucherToEarliestSubscriber(Long voucherId, Long excludeUserId) {
        //查询秒杀优惠券
        SeckillVoucherFullModel seckillVoucherFullModel = seckillVoucherService.queryByVoucherId(voucherId);
        if (Objects.isNull(seckillVoucherFullModel) || Objects.isNull(seckillVoucherFullModel.getBeginTime()) || Objects.isNull(seckillVoucherFullModel.getEndTime())) {
            return false;
        }
        //再加载一次缓存，防止修改数据或对账执行将redis中的库存删除
        seckillVoucherService.loadVoucherStock(voucherId);
        //在订阅队列中查找最早订阅的用户，排除已取消用户
        String candidateUserIdStr = findEarliestCandidate(voucherId, excludeUserId);
        if (StrUtil.isBlank(candidateUserIdStr)) {
            return false;
        }
        return issueToCandidate(voucherId, candidateUserIdStr, seckillVoucherFullModel);
    }

    private String findEarliestCandidate(Long voucherId, Long excludeUserId) {
        //订阅队列的key
        RedisKeyBuild subscribeKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_SUBSCRIBE_ZSET_TAG_KEY, voucherId);
        //已购买用户的key
        RedisKeyBuild purchasedKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_USER_TAG_KEY, voucherId);
        long pageCount = 1L;
        long offset = 0L;
        while (true) {
            Set<ZSetOperations.TypedTuple<String>> page =
                    redisCache.rangeByScoreWithScoreForSortedSet(
                            subscribeKey,
                            Double.NEGATIVE_INFINITY,
                            Double.POSITIVE_INFINITY,
                            offset,
                            pageCount,
                            String.class
                    );
            if (CollectionUtil.isEmpty(page)) {
                return null;
            }
            //取出当前页的唯一用户
            ZSetOperations.TypedTuple<String> tuple = page.iterator().next();
            if (Objects.isNull(tuple) || Objects.isNull(tuple.getValue())) {
                offset++;
                continue;
            }
            //获取用户id
            String userIdStr = tuple.getValue();
            if (StrUtil.isBlank(userIdStr)) {
                offset++;
                continue;
            }
            //排除已购买的用户
            if (Objects.nonNull(excludeUserId) && Objects.equals(String.valueOf(excludeUserId), userIdStr)) {
                offset++;
                continue;
            }
            Boolean purchased = redisCache.isMemberForSet(purchasedKey, userIdStr);
            if (purchased) {
                offset++;
                continue;
            }
            return userIdStr;
        }
    }

    /**
     * 对候选用户执行Lua扣减与消息下发，并从订阅队列中移除
     */
    private boolean issueToCandidate(Long voucherId, String candidateUserIdStr, SeckillVoucherFullModel seckillVoucherFullModel) {
        Long candidateUserId = Long.valueOf(candidateUserIdStr);
        //验证用户等级是否符合
        verifyUserLevel(seckillVoucherFullModel, candidateUserId);
        //构建Lua脚本keys
        List<String> keys = buildSeckillKeys(voucherId);
        long orderId = snowflakeIdGenerator.nextId();
        long traceId = snowflakeIdGenerator.nextId();
        String[] args = buildSeckillArgs(voucherId, candidateUserIdStr, orderId, traceId, seckillVoucherFullModel);
        //执行脚本
        SeckillVoucherDomain domain = seckillVoucherOperate.execute(keys, args);
        if (!Objects.equals(domain.getCode(), BaseCode.SUCCESS.getCode())) {
            log.info("自动发券Lua脚本扣减失败，code={},voucherId={},userId={}", domain.getCode(), voucherId, candidateUserId);
        }
        //扣减成功，向Kafka发送消息
        SeckillVoucherMessage message = SeckillVoucherMessage.builder()
                .userId(candidateUserId)
                .voucherId(voucherId)
                .orderId(orderId)
                .traceId(traceId)
                .beforeQty(domain.getBeforeQty())
                .changeQty(domain.getDeductQty())
                .afterQty(domain.getAfterQty())
                .autoIssue(Boolean.TRUE)
                .build();
        seckillVoucherProducer.sendPayload(SpringUtil.getPrefixDistinctionName() + "-" + SECKILL_VOUCHER_TOPIC, message);

        return true;
    }

    private String[] buildSeckillArgs(Long voucherId,
                                      String candidateUserIdStr,
                                      long orderId,
                                      long traceId,
                                      SeckillVoucherFullModel seckillVoucherFullModel) {
        String[] args = new String[9];
        args[0] = voucherId.toString();
        args[1] = candidateUserIdStr;
        args[2] = String.valueOf(LocalDateTimeUtil.toEpochMilli(seckillVoucherFullModel.getBeginTime()));
        args[3] = String.valueOf(LocalDateTimeUtil.toEpochMilli(seckillVoucherFullModel.getEndTime()));
        args[4] = String.valueOf(seckillVoucherFullModel.getStatus());
        args[5] = String.valueOf(orderId);
        args[6] = String.valueOf(traceId);
        args[7] = String.valueOf(LogType.DEDUCT.getCode());
        args[8] = String.valueOf(computeTtlSeconds(seckillVoucherFullModel));
        return args;
    }

    private long computeTtlSeconds(SeckillVoucherFullModel seckillVoucherFullModel) {
        long seconds = Duration.between(LocalDateTimeUtil.now(), seckillVoucherFullModel.getEndTime()).getSeconds();
        return Math.max(1L, seconds + Duration.ofDays(1).getSeconds());
    }

    private List<String> buildSeckillKeys(Long voucherId) {
        String stockKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_STOCK_TAG_KEY, voucherId).getRelKey();
        String userKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_USER_TAG_KEY, voucherId).getRelKey();
        String traceKey = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_TRACE_LOG_TAG_KEY, voucherId).getRelKey();
        return ListUtil.of(stockKey, userKey, traceKey);
    }
}
