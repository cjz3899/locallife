local stockKey = KEYS[1]
local seckillUserKey = KEYS[2]
local traceLogKey = KEYS[3]
local voucherId = ARGV[1]
local userId = (ARGV[2])
local orderId = ARGV[3]
local seckillVoucherOrderOperate = tonumber(ARGV[4])
local traceId = ARGV[5]
local logType = ARGV[6]
local beforeQty = tonumber(ARGV[7])
local changeQty = tonumber(ARGV[8])
local afterQty = tonumber(ARGV[9])
-- 校验库存 key 是否存在，不存在说明流程状态异常，直接返回错误码 10004
local stock = redis.call('get', stockKey);
if not stock then
    return 10004
end
-- 回滚时将库存 key 删除，恢复为未发布状态
redis.call('del', stockKey)
-- 仅当操作类型为秒杀下单回滚（operate == 1）时，才移除该用户的抢购记录，允许其重新参与
if seckillVoucherOrderOperate == 1 then
    -- 用户若已记录在抢购名单中则移除
    if (redis.call('sismember', seckillUserKey, userId) == 1) then
        redis.call('srem', seckillUserKey, userId)
    end
end
-- 获取 Redis 服务器时间（秒 + 微秒），换算成毫秒时间戳，用于日志记录
local timeArr = redis.call('TIME')
local nowMillis = tonumber(timeArr[1]) * 1000 + math.floor(tonumber(timeArr[2]) / 1000)
-- 组装本次操作的审计日志内容
local logEntry = cjson.encode({
    logType = logType,
    ts = nowMillis,
    orderId = orderId,
    traceId = traceId,
    userId = userId,
    voucherId = voucherId,
    beforeQty = beforeQty,
    changeQty = changeQty,
    afterQty = afterQty
})
-- 以 traceId 为 field 写入 trace 日志 Hash，便于按 traceId 追踪整条链路
redis.call('hset', traceLogKey, traceId, logEntry)
return 0
