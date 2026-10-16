package com.junzhecai.delay.consumer;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.junzhecai.core.ConsumerTask;
import com.junzhecai.core.RedisKeyManage;
import com.junzhecai.core.SpringUtil;
import com.junzhecai.delay.message.DelayedVoucherReminderMessage;
import com.junzhecai.entity.UserInfo;
import com.junzhecai.model.SeckillVoucherFullModel;
import com.junzhecai.redis.RedisCache;
import com.junzhecai.redis.RedisKeyBuild;
import com.junzhecai.service.ISeckillVoucherService;
import com.junzhecai.service.IUserInfoService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.junzhecai.constant.Constant.DELAY_VOUCHER_REMINDER;

@Slf4j
@Component
public class ConsumerDelayedVoucherReminder implements ConsumerTask {
    @Resource
    private RedisCache redisCache;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private IUserInfoService userInfoService;


    @Value("${seckill.reminder.notify.sms.enabled:false}")
    private boolean smsEnabled;

    @Value("${seckill.reminder.notify.app.enabled:false}")
    private boolean appEnabled;

    @Value("${seckill.reminder.notify.sms.to:}")
    private String smsTo;

    @Value("${seckill.reminder.dedup.window.seconds:1800}")
    private long dedupWindowSeconds;

    /**
     * 当优惠券未设置allowedLevels/minLevel时的默认最小会员等级
     *
     */
    @Value("${seckill.reminder.notify.default.minLevel:1}")
    private int defaultMinLevel;
    /**
     * 每次提醒的最大用户数量，防止一次性通知过多
     *
     */
    @Value("${seckill.reminder.notify.max.users:1000}")
    private int maxNotifyUsers;
    /**
     * 是否附加通知“最近购买活跃用户”
     *
     */
    @Value("${seckill.reminder.notify.top.buyers.enabled:true}")
    private boolean topBuyersEnabled;
    /**
     * 统计最近多少天的购买行为
     *
     */
    @Value("${seckill.reminder.notify.top.buyers.days:30}")
    private int topBuyersDays;
    /**
     * Top购买用户数量
     *
     */
    @Value("${seckill.reminder.notify.top.buyers.count:200}")
    private int topBuyersCount;
    /**
     * 最大会员等级
     */
    @Value("${seckill.reminder.notify.user.level.max:10}")
    private int maxUserLevel;

    @Override
    public void execute(String content) {
        try {
            DelayedVoucherReminderMessage msg = parseMessage(content);
            if (Objects.isNull(msg)) {
                return;
            }
            Long voucherId = msg.getVoucherId();
            SeckillVoucherFullModel voucherFull = seckillVoucherService.queryByVoucherId(voucherId);
            if (voucherFull == null) {
                log.warn("[DELAY_REMINDER_CONSUMER] 秒杀券不存在或缓存未命中 voucherId={}", voucherId);
                return;
            }
            //构建目标用户ID集合
            Set<String> userIds = buildAudienceUserIds(voucherFull);
            if (CollectionUtil.isEmpty(userIds)) {
                log.info("[DELAY_REMINDER_CONSUMER] 无符合规则的用户 voucherId={}", voucherId);
                return;
            }
            int notified = notifyUsers(voucherId, msg.getBeginTime(), userIds);
            log.info("[DELAY_REMINDER_CONSUMER] 完成提醒 voucherId={} totalUsers={} notified={}",
                    voucherId, userIds.size(), notified);
        } catch (Exception e) {
            log.warn("[DELAY_REMINDER_CONSUMER] 执行异常", e);
        }
    }

    private DelayedVoucherReminderMessage parseMessage(String content) {
        try {
            DelayedVoucherReminderMessage msg = JSON.parseObject(content, DelayedVoucherReminderMessage.class);
            if (msg == null || msg.getVoucherId() == null) {
                log.warn("[DELAY_REMINDER_CONSUMER] 消息解析失败 content={}", content);
                return null;
            }
            return msg;
        } catch (Exception ex) {
            log.warn("[DELAY_REMINDER_CONSUMER] 消息反序列化异常 content={}", content, ex);
            return null;
        }
    }

    private Set<String> buildAudienceUserIds(SeckillVoucherFullModel voucherFull) {
        //获取允许的会员等级范围
        String allowedLevelsStr = voucherFull.getAllowedLevels();
        Integer minLevel = voucherFull.getMinLevel();
        Long shopId = voucherFull.getShopId();
        /**
         * 根据会员等级范围查询
         */
        //查询符合条件的用户信息
        List<UserInfo> userInfos = queryEligibleUserInfos(allowedLevelsStr, minLevel);
        Set<String> userIds = toUserIdSet(userInfos);

        /**
         * 根据Top买家统计
         */
        if (topBuyersEnabled && Objects.nonNull(shopId)) {
            //从Redis中获取Top买家
            for (Long uid : readTopBuyersFromRedis(shopId, topBuyersCount, topBuyersDays)) {
                if (uid != null) {
                    userIds.add(String.valueOf(uid));
                }
            }
        } else if (topBuyersEnabled) {
            log.warn("[DELAY_REMINDER_CONSUMER] 店铺ID为空，跳过Top买家统计");
        }
        return userIds;
    }

    private List<UserInfo> queryEligibleUserInfos(String allowedLevelsStr, Integer minLevel) {
        if (StrUtil.isNotBlank(allowedLevelsStr)) {
            Set<Integer> allowed = parseAllowedLevels(allowedLevelsStr);
            if (CollectionUtil.isNotEmpty(allowed)) {
                //从Redis的用户等级set中获取用户id
                List<Long> fromRedis = readUserIdsFromLevelSets(new ArrayList<>(allowed), maxNotifyUsers);
                if (CollectionUtil.isNotEmpty(fromRedis)) {
                    List<UserInfo> list = new ArrayList<>(fromRedis.size());
                    for (Long uid : fromRedis) {
                        if (uid != null) {
                            UserInfo u = new UserInfo();
                            u.setUserId(uid);
                            list.add(u);
                        }
                    }
                    return list;
                }
                //Redis中没有，则从数据库中获取
                return userInfoService.lambdaQuery()
                        .select(UserInfo::getUserId, UserInfo::getLevel)
                        .in(UserInfo::getLevel, allowed)
                        .last("limit " + maxNotifyUsers)
                        .list();
            }
            //allowedLevels为空，使用minLevel
            int useMin = Objects.nonNull(minLevel) ? minLevel : defaultMinLevel;
            //构建从minLevel到maxUserLevel的等级范围
            List<Long> fromRedis = readUserIdsFromLevelSets(buildLevelRange(useMin, maxUserLevel), maxNotifyUsers);
            if (CollectionUtil.isNotEmpty(fromRedis)) {
                List<UserInfo> list = new ArrayList<>(fromRedis.size());
                for (Long uid : fromRedis) {
                    if (uid != null) {
                        UserInfo u = new UserInfo();
                        u.setUserId(uid);
                        list.add(u);
                    }
                }
                return list;
            }
            return userInfoService.lambdaQuery()
                    .select(UserInfo::getUserId, UserInfo::getLevel)
                    .ge(UserInfo::getLevel, useMin)
                    .last("limit " + maxNotifyUsers)
                    .list();
        }

        //allowedLevels为空且minLevel不为空
        if (Objects.nonNull(minLevel)) {
            List<Long> fromRedis = readUserIdsFromLevelSets(buildLevelRange(minLevel, maxUserLevel), maxNotifyUsers);
            if (CollectionUtil.isNotEmpty(fromRedis)) {
                List<UserInfo> list = new ArrayList<>(fromRedis.size());
                for (Long uid : fromRedis) {
                    if (uid != null) {
                        UserInfo u = new UserInfo();
                        u.setUserId(uid);
                        list.add(u);
                    }
                }
                return list;
            }
            return userInfoService.lambdaQuery()
                    .select(UserInfo::getUserId, UserInfo::getLevel)
                    .ge(UserInfo::getLevel, minLevel)
                    .last("limit " + maxNotifyUsers)
                    .list();
        }

        //allowedLevels为空且minLevel为空，使用默认最小会员等级
        List<Long> fromRedis = readUserIdsFromLevelSets(buildLevelRange(defaultMinLevel, maxUserLevel), maxNotifyUsers);
        if (CollectionUtil.isNotEmpty(fromRedis)) {
            List<UserInfo> list = new ArrayList<>(fromRedis.size());
            for (Long uid : fromRedis) {
                if (uid != null) {
                    UserInfo u = new UserInfo();
                    u.setUserId(uid);
                    list.add(u);
                }
            }
            return list;
        }
        return userInfoService.lambdaQuery()
                .select(UserInfo::getUserId, UserInfo::getLevel)
                .ge(UserInfo::getLevel, defaultMinLevel)
                .last("limit " + maxNotifyUsers)
                .list();
    }

    private List<Integer> buildLevelRange(int min, int max) {
        int from = Math.max(min, 1);
        int to = Math.max(max, from);
        List<Integer> levels = new ArrayList<>(to - from + 1);
        for (int lv = from; lv <= to; lv++) {
            levels.add(lv);
        }
        return levels;
    }

    private List<Long> readUserIdsFromLevelSets(List<Integer> levels, int count) {
        if (CollectionUtil.isEmpty(levels)) {
            return Collections.emptyList();
        }
        List<RedisKeyBuild> keys = new ArrayList<>(levels.size());
        for (Integer lv : levels) {
            if (lv == null) {
                continue;
            }
            keys.add(RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_USER_LEVEL_MEMBERS_TAG_KEY, lv));
        }
        if (keys.isEmpty()) {
            return Collections.emptyList();
        }
        //单个等级的简化处理，直接从Redis set中随机获取指定数量的不重复用户
        if (keys.size() == 1) {
            Set<Long> r = redisCache.distinctRandomMembersForSet(keys.get(0), Math.max(count, 1), Long.class);
            return new ArrayList<>(r);
        }


        String label;
        //多个等级的处理，先求并集
        //例如等级列表为[1, 2, 3, 4]，则并集的标签为"1-4"
        if (levels.size() >= 2) {
            label = levels.get(0) + "-" + levels.get(levels.size() - 1);
        } else {
            label = String.valueOf(levels.get(0));
        }
        RedisKeyBuild dest = RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_USER_LEVEL_MEMBERS_UNION_TAG_KEY, label);
        //第一个key作为基础key
        RedisKeyBuild base = keys.get(0);
        //其他key作为要合并的key
        Collection<RedisKeyBuild> others = keys.subList(1, keys.size());
        try {
            redisCache.unionAndStoreForSet(base, others, dest);
            redisCache.expire(dest, 60, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[DELAY_REMINDER_CONSUMER] SET并集失败 levels={} label={}", levels, label, e);
        }
        Set<Long> r = redisCache.distinctRandomMembersForSet(dest, Math.max(count, 1), Long.class);
        return new ArrayList<>(r);
    }

    /**
     * 解析允许的会员等级范围
     *
     * @param allowedLevelsStr 允许的会员等级范围，用逗号分隔
     * @return 允许的会员等级范围
     */
    private Set<Integer> parseAllowedLevels(String allowedLevelsStr) {
        Set<Integer> allowed = new HashSet<>();
        if (StrUtil.isBlank(allowedLevelsStr)) {
            return allowed;
        }
        String[] parts = allowedLevelsStr.split(",");
        for (String s : parts) {
            if (StrUtil.isNotBlank(s)) {
                try {
                    allowed.add(Integer.valueOf(s.trim()));
                } catch (Exception ignore) {

                }
            }
        }
        return allowed;
    }

    private Set<String> toUserIdSet(List<UserInfo> userInfos) {
        Set<String> userIds = new LinkedHashSet<>();
        if (CollectionUtil.isEmpty(userInfos)) {
            return userIds;
        }
        for (UserInfo ui : userInfos) {
            if (Objects.nonNull(ui) && Objects.nonNull(ui.getUserId())) {
                userIds.add(String.valueOf(ui.getUserId()));
            }
        }
        return userIds;
    }

    /**
     * 从Redis读取店铺Top买家（按消费金额排序的ZSet）
     * 多天数据需要ZUNIONSTORE合并后再取Top N
     *
     * @param shopId 店铺ID
     * @param count  需要的Top买家数量
     * @param days   统计最近多少天的购买行为
     * @return Top买家的用户ID列表，读取失败时返回空列表
     */
    private List<Long> readTopBuyersFromRedis(Long shopId, int count, int days) {
        try {
            LocalDate today = LocalDate.now();
            // BASIC_ISO_DATE 格式如 20261015，作为每日ZSet key的日期后缀
            DateTimeFormatter fmt = DateTimeFormatter.BASIC_ISO_DATE;
            // 按天构建ZSet的key列表：今天、昨天、前天……共days个
            List<RedisKeyBuild> dailyKeys = new ArrayList<>();
            for (int i = 0; i < Math.max(days, 1); i++) {
                String day = today.minusDays(i).format(fmt);
                dailyKeys.add(RedisKeyBuild.createRedisKey(
                        RedisKeyManage.SECKILL_SHOP_TOP_BUYERS_DAILY_TAG_KEY,
                        shopId,
                        day
                ));
            }
            if (dailyKeys.isEmpty()) {
                return Collections.emptyList();
            }
            // 只有1天数据时无需合并，直接取该ZSet的倒序前count个元素
            if (dailyKeys.size() == 1) {
                Set<Long> topSet = redisCache.getReverseRangeForSortedSet(
                        dailyKeys.get(0), 0, Math.max(count - 1, 0), Long.class);
                return new ArrayList<>(topSet);
            }
            // 多天数据：生成带日期范围标签的合并目标key，如 "20260915-20261015"
            String rangeLabel = today.minusDays(dailyKeys.size() - 1).format(fmt) + "-" + today.format(fmt);
            RedisKeyBuild destKey = RedisKeyBuild.createRedisKey(
                    RedisKeyManage.SECKILL_SHOP_TOP_BUYERS_UNION_TAG_KEY,
                    shopId,
                    rangeLabel
            );
            // 以第1天的key为基准，与其余天的key做ZUNIONSTORE合并（同用户分数累加）
            RedisKeyBuild base = dailyKeys.get(0);
            Collection<RedisKeyBuild> others = dailyKeys.subList(1, dailyKeys.size());
            try {
                redisCache.unionAndStoreForSortedSet(base, others, destKey);
                // 合并结果只是临时中间产物，设置短过期避免占用内存
                redisCache.expire(destKey, 60, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("[DELAY_REMINDER_CONSUMER] ZSET并集失败 shopId={} range={}", shopId, rangeLabel, e);
            }
            // 从合并结果中取倒序（分数最高）的前count个买家
            Set<Long> topSet = redisCache.getReverseRangeForSortedSet(destKey, 0, Math.max(count - 1, 0), Long.class);
            return new ArrayList<>(topSet);
        } catch (Exception ex) {
            // 整体兜底：Redis异常不阻塞提醒主流程，降级为不通知Top买家
            log.warn("[DELAY_REMINDER_CONSUMER] 读取Redis Top买家失败 shopId={} days={} count={} ex={}",
                    shopId, days, count, ex.getMessage());
            return Collections.emptyList();
        }
    }

    private int notifyUsers(Long voucherId, LocalDateTime beginTime, Set<String> userIds) {
        int notifyCount = 0;
        for (String userIdStr : userIds) {
            if (StrUtil.isBlank(userIdStr)) {
                continue;
            }
            boolean shouldNotify;
            try {
                // setIfAbsent是原子操作，不会重复设置
                shouldNotify = redisCache.setIfAbsent(
                        RedisKeyBuild.createRedisKey(RedisKeyManage.SECKILL_REMINDER_NOTIFY_DEDUP_KEY, voucherId, userIdStr),
                        "1",
                        dedupWindowSeconds,
                        TimeUnit.SECONDS
                );
            } catch (Exception e) {
                shouldNotify = true;
            }
            // key已存在 = 已经通知过这个用户了，跳过，不重复发
            if (!shouldNotify) {
                continue;
            }
            String notifyContent = String.format("[REMINDER] voucherId=%s userId=%s beginTime=%s",
                    voucherId, userIdStr, beginTime);
            if (smsEnabled && StrUtil.isNotBlank(smsTo)) {
                log.info("[REMINDER_SMS] to={} content={}", smsTo, notifyContent);
            }
            if (appEnabled) {
                log.info("[REMINDER_APP] userId={} content={}", userIdStr, notifyContent);
            }
            notifyCount++;
        }
        return notifyCount;
    }

    @Override
    public String topic() {
        return SpringUtil.getPrefixDistinctionName() + "-" + DELAY_VOUCHER_REMINDER;
    }
}
