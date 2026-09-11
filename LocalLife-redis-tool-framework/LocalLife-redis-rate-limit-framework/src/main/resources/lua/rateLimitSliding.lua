-- 令牌桶限流（支持按 IP 与用户两个维度）
-- 设计说明：
-- 容量 capacity = maxAttempts（突发能力由容量决定）
-- 生成速率 ratePerMs = maxAttempts / windowMillis（平均速率）
-- 使用 Redis TIME 作为统一时钟，避免应用与Redis时钟漂移
-- TTL = windowMillis*2 + 随机抖动(window/10)，缓解集中过期抖动
-- 判定顺序：先按IP维度，再按用户维度（用户维度更细粒度）

-- IP维度桶（HASH），可选
local ipKey = KEYS[1]
-- 用户维度桶（HASH），必须
local userKey = KEYS[2]
-- IP窗口毫秒数（用于推导速率与TTL）
local ipLimitWindowMillis = tonumber(ARGV[1] or '0')
-- IP最大尝试次数（映射为桶容量）
local ipLimitMaxAttempts = tonumber(ARGV[2] or '0')
-- 用户窗口毫秒数（用于推导速率与TTL）
local userLimitWindowMillis = tonumber(ARGV[3] or '0')
-- 用户最大尝试次数（映射为桶容量）
local userLimitMaxAttempts = tonumber(ARGV[4] or '0')

-- 返回码与BaseCode保持一致
local CODE_SUCCESS = 0 -- 允许
local CODE_IP_EXCEEDED = 10007 -- IP 维度限流（超限）
local CODE_USER_EXCEEDED = 10008 -- 用户维度限流（超限）

-- 当前毫秒时间：TIME 返回 [seconds, microseconds]
local now = redis.call('TIME')
-- 统一时间基于Redis服务器
local nowMillis = now[1] * 1000 + math.floor(now[2] / 1000)

-- 执行业务

-- 生成唯一成员值，避免同毫秒内重复
local function uniqueMember(baseKey, ts)
    local seqKey = baseKey .. ':' .. ts
    local seq = redis.call('INCR', seqKey)
    -- 给序列key设置一个较短过期，避免长时间占用
    if seq == 1 then
        redis.call('EXPIRE', seqKey, 600000)
    end
    return tostring(ts) .. ':' .. tostring(seq)
end

-- 对指定ZSET执行滑动窗口计数，超过则返回指定错误码
local function checkSlidingLimit(zsetKey, windowMillis, maxAttempts, exceededCode)
    if zsetKey ~= nil and zsetKey ~= '' and windowMillis > 0 and maxAttempts > 0 then
        -- 添加当前记录
        local member = uniqueMember(zsetKey, nowMillis)
        redis.call('ZADD', zsetKey, nowMillis, member)

        -- 删除窗口外的旧记录(用 -inf 更稳)
        local maxOld = nowMillis - windowMillis
        redis.call('ZREMRANGEBYSCORE', zsetKey, '-inf', maxOld)

        -- 统计当前窗口内的调用次数
        local count = redis.call('ZCARD', zsetKey)

        -- 首次创建时设过期(注意 EXPIRE 单位是秒)
        if count == 1 then
            redis.call('EXPIRE', zsetKey, math.ceil(windowMillis / 1000) * 2)
        end

        if count > maxAttempts then
            return exceededCode
        end
    end
    return CODE_SUCCESS
end

-- 先检查IP维度
local ipRet = CODE_IP_EXCEEDED

if ipKey ~= nil and ipKey ~= '' and ipLimitWindowMillis > 0 and ipLimitMaxAttempts > 0 then
    ipRet = checkSlidingLimit(ipKey, ipLimitWindowMillis, ipLimitMaxAttempts, CODE_IP_EXCEEDED)
    if ipRet ~= CODE_SUCCESS then
        return ipRet
    end
end

-- 再检查用户维度
local userRet = checkSlidingLimit(userKey, userLimitWindowMillis, userLimitMaxAttempts, CODE_USER_EXCEEDED)
if userRet ~= CODE_SUCCESS then
    return userRet
end
return CODE_SUCCESS




