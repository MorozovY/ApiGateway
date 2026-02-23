# Rate Limiting — Техническая документация

Эта документация описывает архитектуру и реализацию rate limiting в API Gateway.

## Содержание

- [Введение](#введение)
- [Token Bucket Algorithm](#token-bucket-algorithm)
- [Redis Data Structure](#redis-data-structure)
- [Архитектура компонентов](#архитектура-компонентов)
- [Fallback механизм](#fallback-механизм)
- [Debugging & Testing](#debugging--testing)

---

## Введение

### Почему Lua для Rate Limiting?

Rate limiting требует **атомарных операций** для корректной работы в распределённой среде. Рассмотрим сценарий без атомарности:

```
Время    Инстанс A           Инстанс B
──────────────────────────────────────────────────
t1       GET tokens = 1
t2                           GET tokens = 1
t3       tokens >= 1? ✓
t4                           tokens >= 1? ✓
t5       SET tokens = 0
t6                           SET tokens = 0  ← Оба пропустили запрос!
```

При burst=1 оба запроса прошли, хотя должен был пройти только один.

**Решение: Redis Lua Scripts**

Lua скрипт выполняется атомарно на стороне Redis (команда `EVAL`). Это гарантирует:

1. **Атомарность** — вся логика (read + compute + write) выполняется без прерываний
2. **Консистентность** — никакой другой клиент не может изменить данные между операциями
3. **Производительность** — один round-trip вместо нескольких

```
Время    Инстанс A                    Инстанс B
──────────────────────────────────────────────────
t1       EVAL script (атомарно)
         → GET tokens = 1
         → tokens >= 1? ✓
         → SET tokens = 0
         → return allowed=1
t2                                    EVAL script (атомарно)
                                      → GET tokens = 0
                                      → tokens >= 1? ✗
                                      → return allowed=0
```

---

## Token Bucket Algorithm

### Концепция

Token bucket — классический алгоритм rate limiting. Представьте ведро (bucket), в которое:
- Токены добавляются с постоянной скоростью (`requestsPerSecond`)
- Максимальное количество токенов ограничено (`burstSize`)
- Каждый запрос требует 1 токен

```
     burstSize = 5                    burstSize = 5
        ┌───┐                            ┌───┐
        │ ● │  ← tokens = 5              │   │  ← tokens = 0
        │ ● │                            │   │
        │ ● │                            │   │
        │ ● │                            │   │
        │ ● │                            │   │
        └───┘                            └───┘
   Полный bucket                    Пустой bucket
   (все запросы проходят)           (HTTP 429)
```

### Параметры алгоритма

| Параметр | Описание | Пример |
|----------|----------|--------|
| `requestsPerSecond` | Скорость восполнения токенов | 10 токенов/сек |
| `burstSize` | Максимум токенов (burst capacity) | 50 токенов |

**Пример:** При `requestsPerSecond=10` и `burstSize=50`:
- Клиент может отправить 50 запросов мгновенно (burst)
- После этого — 10 запросов в секунду устойчиво
- Если нет запросов 5 сек, bucket снова полный (50 токенов)

### Реализация (Lua скрипт)

Скрипт расположен в: `backend/gateway-core/src/main/resources/scripts/token-bucket.lua`

```lua
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

-- Рассчитываем восполнение токенов
local elapsed = (now - lastRefill) / 1000.0  -- в секундах
local refill = elapsed * rate  -- дробное восполнение

-- Добавляем токены (ограничено capacity)
tokens = math.max(0, math.min(capacity, tokens + refill))
lastRefill = now

-- Проверяем доступность токена
local allowed = 0
if tokens >= 1 then
    tokens = tokens - 1
    allowed = 1
end

-- Сохраняем состояние
redis.call('HSET', key, 'tokens', tostring(tokens), 'lastRefill', lastRefill)
redis.call('EXPIRE', key, ttl)

-- Рассчитываем время reset (когда bucket будет полностью восполнен)
local tokensNeeded = capacity - tokens
local secondsToFull = 0
if tokensNeeded > 0 and rate > 0 then
    secondsToFull = math.ceil(tokensNeeded / rate)
end
local resetTime = now + (secondsToFull * 1000)

-- Возвращаем: [allowed, remaining, resetTime]
return {allowed, math.floor(tokens), resetTime}
```

### Дробные токены (исправление math.floor бага)

**Проблема (Story 10.1):** Изначально токены хранились как целые числа (`math.floor`). При коротких интервалах между запросами:

```
rate = 1 req/s, elapsed = 0.5s
refill = 0.5 * 1 = 0.5
math.floor(0.5) = 0  ← Потеря токена!
```

За 2 секунды должно накопиться 2 токена, но накапливалось 0.

**Решение:** Токены хранятся как дробные числа (`tostring(tokens)`), а `math.floor` применяется только в ответе клиенту:

```lua
-- Хранение (дробное)
redis.call('HSET', key, 'tokens', tostring(tokens), ...)

-- Ответ клиенту (целое, для X-RateLimit-Remaining)
return {allowed, math.floor(tokens), resetTime}
```

---

## Redis Data Structure

### Формат ключа

```
ratelimit:{routeId}:{clientKey}
```

| Компонент | Описание | Пример |
|-----------|----------|--------|
| `ratelimit` | Префикс (настраиваемый) | `ratelimit` |
| `routeId` | UUID маршрута | `550e8400-e29b-41d4-a716-446655440000` |
| `clientKey` | IP клиента | `192.168.1.100` |

**Пример ключа:**
```
ratelimit:550e8400-e29b-41d4-a716-446655440000:192.168.1.100
```

### Hash Fields

Каждый ключ — Redis Hash с двумя полями:

| Поле | Тип | Описание |
|------|-----|----------|
| `tokens` | string (float) | Текущее количество токенов (дробное) |
| `lastRefill` | string (long) | Timestamp последнего восполнения (мс) |

**Пример данных:**
```
HGETALL ratelimit:550e8400-...:192.168.1.100
1) "tokens"
2) "14.5"
3) "lastRefill"
4) "1708956000000"
```

### TTL Handling

- **Значение по умолчанию:** 120 секунд
- **Обновление:** При каждом запросе (`EXPIRE key ttl`)
- **Назначение:** Автоочистка неактивных bucket

```kotlin
// TokenBucketScript.kt:78
const val DEFAULT_TTL_SECONDS = 120
```

**Почему 120 секунд?**
- Достаточно для хранения состояния между запросами
- Автоматическая очистка при отсутствии активности
- Не накапливает memory при атаках с уникальными IP

---

## Архитектура компонентов

### Component Diagram

```
                                   Request
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         RateLimitFilter                             │
│  (GlobalFilter, order=10)                                          │
│                                                                     │
│  - Извлекает routeId, rateLimit из exchange attributes              │
│  - Получает clientKey (IP) из X-Forwarded-For или remoteAddress    │
│  - Добавляет X-RateLimit-* headers в ответ                          │
│  - Возвращает HTTP 429 при превышении лимита (RFC 7807)             │
└─────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────┐
│                         RateLimitService                            │
│                                                                     │
│  - Координирует Redis (primary) и LocalRateLimiter (fallback)       │
│  - Формирует ключ: ratelimit:{routeId}:{clientKey}                  │
│  - Логирует переключение между режимами                             │
└─────────────────────────────────────────────────────────────────────┘
                     │                              │
           ┌─────────┴─────────┐          ┌────────┴────────┐
           ▼                   ▼          ▼                 ▼
┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────────┐
│   TokenBucketScript │  │      Redis          │  │  LocalRateLimiter   │
│                     │  │                     │  │                     │
│ - Загружает Lua     │  │ - EVAL token-bucket │  │ - Caffeine cache    │
│ - Выполняет EVAL    │◄─┤ - HMGET/HSET/EXPIRE │  │ - 50% от policy     │
│ - Парсит результат  │  │ - Атомарно          │  │ - Per-instance      │
└─────────────────────┘  └─────────────────────┘  └─────────────────────┘
```

### Файлы компонентов

| Компонент | Путь | Назначение |
|-----------|------|------------|
| **Lua скрипт** | `backend/gateway-core/src/main/resources/scripts/token-bucket.lua` | Атомарный token bucket алгоритм |
| **TokenBucketScript** | `backend/gateway-core/src/main/kotlin/com/company/gateway/core/ratelimit/TokenBucketScript.kt` | Kotlin wrapper для Lua, выполнение EVAL |
| **RateLimitService** | `backend/gateway-core/src/main/kotlin/com/company/gateway/core/ratelimit/RateLimitService.kt` | Координатор Redis + fallback |
| **LocalRateLimiter** | `backend/gateway-core/src/main/kotlin/com/company/gateway/core/ratelimit/LocalRateLimiter.kt` | In-memory fallback (Caffeine) |
| **RateLimitFilter** | `backend/gateway-core/src/main/kotlin/com/company/gateway/core/filter/RateLimitFilter.kt` | GlobalFilter с X-RateLimit headers |
| **RateLimitResult** | `backend/gateway-core/src/main/kotlin/com/company/gateway/core/ratelimit/RateLimitResult.kt` | DTO результата проверки |

### Ключевые методы

**TokenBucketScript.checkRateLimit()** — выполнение Lua скрипта:

```kotlin
// TokenBucketScript.kt:40-71
fun checkRateLimit(
    key: String,
    requestsPerSecond: Int,
    burstSize: Int,
    ttlSeconds: Int = DEFAULT_TTL_SECONDS
): Mono<RateLimitResult> {
    val now = System.currentTimeMillis()

    return redisTemplate.execute(
        script,
        listOf(key),
        listOf(
            requestsPerSecond.toString(),
            burstSize.toString(),
            now.toString(),
            ttlSeconds.toString()
        )
    )
    .next()
    .map { result ->
        val list = result as List<Long>
        RateLimitResult(
            allowed = list[0] == 1L,
            remaining = list[1].toInt(),
            resetTime = list[2]
        )
    }
}
```

**RateLimitFilter.filter()** — интеграция в gateway pipeline:

```kotlin
// RateLimitFilter.kt:61-87
override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
    val routeId = exchange.getAttribute<UUID>(ROUTE_ID_ATTRIBUTE)
    val rateLimit = exchange.getAttribute<RateLimit>(RATE_LIMIT_ATTRIBUTE)

    // Если нет rate limit политики — пропускаем
    if (rateLimit == null || routeId == null) {
        return chain.filter(exchange)
    }

    val clientKey = extractClientKey(exchange)

    return rateLimitService.checkRateLimit(routeId, clientKey, rateLimit)
        .flatMap { result ->
            if (result.allowed) {
                addRateLimitHeaders(exchange, rateLimit, result)
                chain.filter(exchange)
            } else {
                rejectRequest(exchange, rateLimit, result)  // HTTP 429
            }
        }
}
```

### Response Headers

При успешном запросе добавляются информационные заголовки:

| Header | Описание | Пример |
|--------|----------|--------|
| `X-RateLimit-Limit` | Лимит (requests per second) | `10` |
| `X-RateLimit-Remaining` | Оставшиеся токены | `8` |
| `X-RateLimit-Reset` | Unix timestamp восстановления | `1708956120` |

При превышении лимита (HTTP 429):

| Header | Описание | Пример |
|--------|----------|--------|
| `Retry-After` | Секунды до восстановления | `3` |
| `Content-Type` | `application/json` | — |

**Тело ответа (RFC 7807):**
```json
{
    "type": "https://api.gateway/errors/rate-limit-exceeded",
    "title": "Too Many Requests",
    "status": 429,
    "detail": "Rate limit exceeded. Try again in 3 seconds.",
    "correlationId": "abc-123-def"
}
```

---

## Fallback механизм

### Когда активируется Fallback?

Fallback активируется при недоступности Redis:
- Connection timeout
- Connection refused
- Redis command error

### Поведение

1. **Первое переключение:** Логируется WARNING
2. **Последующие ошибки:** Тихо используется fallback
3. **Восстановление Redis:** Автоматический возврат к distributed режиму

```kotlin
// RateLimitService.kt:73-103
private fun handleRedisError(
    ex: Throwable,
    bucketKey: String,
    rateLimit: RateLimit
): Mono<RateLimitResult> {
    // Логируем только при первом переключении
    if (usingFallback.compareAndSet(false, true)) {
        logger.warn("Rate limiting disabled: Redis unavailable - {}", ex.message)
    }

    if (!fallbackEnabled) {
        // Fallback отключён — пропускаем без проверки
        return Mono.just(RateLimitResult(allowed = true, ...))
    }

    // Используем локальный rate limiter
    return Mono.just(localRateLimiter.checkRateLimit(bucketKey, ...))
}
```

### Консервативные лимиты

LocalRateLimiter применяет **50% от policy** по умолчанию:

```kotlin
// LocalRateLimiter.kt:48-73
fun checkRateLimit(
    key: String,
    requestsPerSecond: Int,
    burstSize: Int
): RateLimitResult {
    // Применяем консервативные лимиты
    val reducedRate = (requestsPerSecond * fallbackReduction).toInt().coerceAtLeast(1)
    val reducedBurst = (burstSize * fallbackReduction).toInt().coerceAtLeast(1)
    // ...
}
```

**Почему 50%?**
- Защита upstream при отсутствии distributed координации
- Каждый инстанс gateway имеет свой локальный кэш
- При N инстансах реальный лимит: N × 50% = N/2 × policy

### Конфигурация

```yaml
# application.yml
gateway:
  ratelimit:
    fallback-enabled: true          # Включить fallback
    fallback-reduction: 0.5         # Коэффициент снижения (50%)
    local-cache-ttl-seconds: 60     # TTL локального кэша
    redis-key-prefix: ratelimit     # Префикс ключей Redis
```

---

## Debugging & Testing

### Redis CLI — Просмотр данных

```bash
# Подключение к Redis
docker exec -it apigateway-redis-1 redis-cli

# Просмотр всех rate limit ключей
KEYS ratelimit:*

# Просмотр конкретного bucket
HGETALL ratelimit:{routeId}:{clientKey}

# Пример:
HGETALL ratelimit:550e8400-e29b-41d4-a716-446655440000:192.168.1.100
# 1) "tokens"
# 2) "14.5"
# 3) "lastRefill"
# 4) "1708956000000"

# Просмотр TTL
TTL ratelimit:{routeId}:{clientKey}

# Удаление ключа (сброс лимита)
DEL ratelimit:{routeId}:{clientKey}

# Удаление всех rate limit ключей (осторожно!)
EVAL "return redis.call('del', unpack(redis.call('keys', 'ratelimit:*')))" 0
```

### Тестирование с curl

```bash
# Тест rate limit на маршруте
for i in {1..20}; do
  curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/orders
done

# Вывод:
# 200
# 200
# ... (до исчерпания burst)
# 429
# 429
```

### Integration Tests с Testcontainers

Файл: `backend/gateway-core/src/test/kotlin/.../ratelimit/TokenBucketScriptTest.kt`

```kotlin
@Testcontainers
class TokenBucketScriptTest {

    companion object {
        @Container
        @JvmStatic
        val redis = RedisContainer("redis:7")
    }

    @Test
    fun `AC1 - первый запрос разрешён и инициализирует bucket с полным burst`() {
        val key = "ratelimit:${UUID.randomUUID()}:client1"

        StepVerifier.create(tokenBucketScript.checkRateLimit(key, 10, 15))
            .assertNext { result ->
                assertThat(result.allowed).isTrue()
                assertThat(result.remaining).isEqualTo(14)  // 15 - 1
            }
            .verifyComplete()
    }

    @Test
    fun `AC2 - превышение burst возвращает allowed=false`() {
        val key = "ratelimit:${UUID.randomUUID()}:client1"
        val burstSize = 3

        // Исчерпываем все токены
        repeat(burstSize) {
            tokenBucketScript.checkRateLimit(key, 10, burstSize).block()
        }

        // Следующий запрос отклонён
        StepVerifier.create(tokenBucketScript.checkRateLimit(key, 10, burstSize))
            .assertNext { result ->
                assertThat(result.allowed).isFalse()
            }
            .verifyComplete()
    }

    @Test
    fun `AC3 - токены восполняются со временем`() {
        // Исчерпываем токены
        repeat(burstSize) { ... }

        // Ждём 500мс (при rate=10/s должно восполниться ~5 токенов)
        Thread.sleep(550)

        // Теперь запросы проходят
        assertThat(result.allowed).isTrue()
    }
}
```

### Запуск тестов

```bash
# Все тесты rate limiting
./gradlew :gateway-core:test --tests "*RateLimit*"

# Только TokenBucketScriptTest
./gradlew :gateway-core:test --tests "TokenBucketScriptTest"

# С подробным выводом
./gradlew :gateway-core:test --tests "*RateLimit*" --info
```

### Мониторинг метрик

Rate limiting метрики экспортируются в Prometheus:

```promql
# Количество отклонённых запросов (429)
rate(gateway_request_total{status="429"}[5m])

# Процент отклонённых запросов по маршруту
rate(gateway_request_total{status="429", route_path="/api/orders"}[5m])
/ rate(gateway_request_total{route_path="/api/orders"}[5m])
```

---

## References

- [Redis EVAL command](https://redis.io/commands/eval/) — выполнение Lua скриптов
- [Token Bucket Algorithm](https://en.wikipedia.org/wiki/Token_bucket) — теоретические основы
- [RFC 7807 Problem Details](https://tools.ietf.org/html/rfc7807) — формат ошибок
- [Spring Cloud Gateway Rate Limiting](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway/gatewayfilter-factories/requestratelimiter-factory.html) — документация Spring

---

*Последнее обновление: 2026-02-23*
