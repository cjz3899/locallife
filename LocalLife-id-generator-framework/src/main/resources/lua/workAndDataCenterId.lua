-- redis中work_id的key
local snowflake_work_id_key = KEYS[1]
-- redis中data_center_id的key
local snowflake_data_center_id_key = KEYS[2]
-- 传进来的是字符串，需要转换成数字
-- work_id的最大阈值
local max_worker_id = tonumber(ARGV[1])
-- data_center_id的最大阈值
local max_data_center_id = tonumber(ARGV[2])
-- 返回的work_id
local return_worker_id = 0
-- 返回的data_center_id
local return_data_center_id = 0
local snowflake_work_id_flag = false
local snowflake_data_center_id_flag = false
local json_result = string.format('{"%s": %d, "%s": %d}',
        'workId', return_worker_id,
        'dataCenterId', return_data_center_id)

-- 判断redis中是否存在work_id和data_center_id，不存在则创建，并设置初始值为0
if (redis.call('exists', snowflake_work_id_key) == 0) then
    redis.call('set', snowflake_work_id_key, 0)
    snowflake_work_id_flag = true
end
if (redis.call('exists', snowflake_data_center_id_key) == 0) then
    redis.call('set', snowflake_data_center_id_key, 0)
    snowflake_data_center_id_flag = true
end

-- 如果redis中不存在work_id和data_center_id，则返回初始值0
if (snowflake_work_id_flag and snowflake_data_center_id_flag) then
    return json_result
end

local snowflake_work_id = tonumber(redis.call('get', snowflake_work_id_key))
local snowflake_data_center_id = tonumber(redis.call('get', snowflake_data_center_id_key))

-- 如果work_id和data_center_id都达到最大值，则将work_id和data_center_id都置为0
if (snowflake_work_id == max_worker_id) then
    if (snowflake_data_center_id == max_data_center_id) then
        redis.call('set', snowflake_work_id_key, 0)
        redis.call('set', snowflake_data_center_id_key, 0)
        -- 如果只有work_id达到最大值，则将data_center_id加1
    else
        return_data_center_id = redis.call('incr', snowflake_data_center_id_key)
    end
    -- 如果都没有达到最大值，则将work_id加1
else
    return_worker_id = redis.call('incr', snowflake_work_id_key)
end
return string.format('{"%s": %d, "%s": %d}',
        'workId', return_worker_id,
        'dataCenterId', return_data_center_id)
