-- KEYS[1] = workId key
-- KEYS[2] = dataCenterId key
-- ARGV[1] = maxWorkerId
-- ARGV[2] = maxDataCenterId

local work_key = KEYS[1]
local dc_key = KEYS[2]
local max_worker = tonumber(ARGV[1])
local max_dc = tonumber(ARGV[2])

if not max_worker or not max_dc or max_worker < 0 or max_dc < 0 then
    return redis.error_reply("invalid max_worker_id / max_data_center_id")
end

local worker = tonumber(redis.call('GET', work_key))
local dc = tonumber(redis.call('GET', dc_key))

-- 初始化：第一次进入，返回 (0,0) 并占用它，存储推进到 (0,1)
if worker == nil and dc == nil then
    redis.call('SET', work_key, 0)
    redis.call('SET', dc_key, 1)
    return cjson.encode({ workId = 0, dataCenterId = 0 })
end

-- 兼容只缺一个 key 的情况
if worker == nil then
    worker = 0
end
if dc == nil then
    dc = 0
end

-- 当前要分配的值就是 (worker, dc)，分配后推进
local cur_worker, cur_dc = worker, dc

-- 先推进 dc
if dc >= max_dc then
    dc = 0
    if worker >= max_worker then
        worker = 0
    else
        worker = worker + 1
    end
else
    dc = dc + 1
end

redis.call('SET', work_key, worker)
redis.call('SET', dc_key, dc)

return cjson.encode({ workId = cur_worker, dataCenterId = cur_dc })