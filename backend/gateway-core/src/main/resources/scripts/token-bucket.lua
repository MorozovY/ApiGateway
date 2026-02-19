-- Token Bucket алгоритм для rate limiting
-- Ключ: ratelimit:{routeId}:{clientKey}
-- KEYS[1] = ключ для rate limit данных
-- ARGV[1] = requestsPerSecond (rate восполнения)
-- ARGV[2] = burstSize (максимум токенов)
-- ARGV[3] = текущее время (unix timestamp в миллисекундах)
-- ARGV[4] = TTL в секундах

local key = KEYS[1]
local rate = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local ttl = tonumber(ARGV[4])

-- Получаем текущее состояние bucket
local bucket = redis.call('HMGET', key, 'tokens', 'lastRefill')
local tokens = tonumber(bucket[1])
local lastRefill = tonumber(bucket[2])

-- Инициализация при первом запросе
if tokens == nil then
    tokens = capacity
    lastRefill = now
end

-- Рассчитываем восполнение токенов на основе прошедшего времени
local elapsed = (now - lastRefill) / 1000.0  -- в секундах
local refill = math.floor(elapsed * rate)

if refill > 0 then
    tokens = math.min(capacity, tokens + refill)
    lastRefill = now
end

-- Проверяем доступность токена
local allowed = 0
if tokens >= 1 then
    tokens = tokens - 1
    allowed = 1
end

-- Сохраняем состояние
redis.call('HSET', key, 'tokens', tokens, 'lastRefill', lastRefill)
redis.call('EXPIRE', key, ttl)

-- Рассчитываем время reset (когда bucket будет полностью восполнен)
local tokensNeeded = capacity - tokens
local secondsToFull = 0
if tokensNeeded > 0 and rate > 0 then
    secondsToFull = math.ceil(tokensNeeded / rate)
end
local resetTime = now + (secondsToFull * 1000)

-- Возвращаем: [allowed (0/1), remaining tokens, reset time (ms)]
return {allowed, tokens, resetTime}
