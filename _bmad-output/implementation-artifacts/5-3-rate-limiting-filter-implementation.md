# Story 5.3: Rate Limiting Filter Implementation

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **Gateway System**,
I want to enforce rate limits on incoming requests,
so that upstream services are protected from overload (FR16).

## Acceptance Criteria

**AC1 — Успешные запросы в пределах лимита:**

**Given** published маршрут с rate limit политикой: 10 req/s, burst 15
**When** клиент отправляет 10 запросов в течение 1 секунды
**Then** все 10 запросов проксируются на upstream
**And** Redis счётчик инкрементируется для каждого запроса

**AC2 — Rate limiting при превышении burst:**

**Given** published маршрут с rate limit политикой: 10 req/s, burst 15
**When** клиент отправляет 20 запросов в течение 1 секунды
**Then** первые 15 запросов успешно проходят (burst allowance)
**And** остальные 5 запросов получают HTTP 429 Too Many Requests
**And** response включает заголовки:
- `X-RateLimit-Limit: 10`
- `X-RateLimit-Remaining: 0`
- `X-RateLimit-Reset: <unix-timestamp>`
- `Retry-After: <seconds>`

**AC3 — Token bucket replenishment:**

**Given** rate limit с token bucket алгоритмом
**When** токены восполняются со временем
**Then** скорость восполнения равна `requestsPerSecond`
**And** максимум токенов равен `burstSize`

**AC4 — Graceful degradation (Redis недоступен):**

**Given** Redis недоступен
**When** проверка rate limit выполняется
**Then** запрос пропускается (graceful degradation — NFR7)
**And** логируется warning: "Rate limiting disabled: Redis unavailable"
**And** используется Caffeine local cache как fallback с консервативными лимитами

**AC5 — Маршрут без rate limit:**

**Given** published маршрут без назначенной политики rate limit (rateLimitId = null)
**When** запросы выполняются
**Then** rate limiting не применяется
**And** запросы проходят без заголовков rate limit

**AC6 — Distributed rate limiting:**

**Given** несколько инстансов gateway
**When** rate limiting одного и того же маршрута
**Then** лимиты разделяются через Redis (distributed rate limiting)
**And** общий rate по всем инстансам соответствует политике

**AC7 — Rate limit заголовки в успешных ответах:**

**Given** published маршрут с rate limit политикой
**When** запрос успешно проходит проверку rate limit
**Then** response включает информационные заголовки:
- `X-RateLimit-Limit: <requestsPerSecond>`
- `X-RateLimit-Remaining: <оставшиеся токены>`
- `X-RateLimit-Reset: <unix-timestamp следующего сброса>`

## Tasks / Subtasks

- [x] Task 1: Создать RateLimitRepository в gateway-core (AC1, AC6)
  - [x] Интерфейс для получения RateLimit по ID
  - [x] Кэширование политик в Caffeine для быстрого доступа
  - [x] JOIN запрос для получения route + rate_limit данных

- [x] Task 2: Создать RateLimitFilter — GlobalFilter для rate limiting (AC1-AC7)
  - [x] Реализовать GlobalFilter с order после CorrelationIdFilter, но до LoggingFilter
  - [x] Получение rateLimitId из маршрута через атрибуты exchange
  - [x] Пропуск проверки если rateLimitId = null (AC5)
  - [x] Интеграция с Redis для distributed token bucket
  - [x] Добавление X-RateLimit-* заголовков в ответ

- [x] Task 3: Реализовать Token Bucket алгоритм через Redis Lua script (AC1, AC2, AC3, AC6)
  - [x] Redis Lua script для атомарного token bucket
  - [x] Ключ: `ratelimit:{routeId}:{clientIdentifier}`
  - [x] Возврат: allowed (boolean), remaining (int), resetTime (unix timestamp)
  - [x] Поддержка TTL для автоматической очистки

- [x] Task 4: Реализовать Caffeine fallback при недоступности Redis (AC4)
  - [x] Локальный in-memory rate limiter через Caffeine
  - [x] Консервативные лимиты (50% от policy) при fallback
  - [x] Логирование warning при переключении на fallback
  - [x] Периодическая проверка доступности Redis для восстановления

- [x] Task 5: Интегрировать RateLimitFilter с DynamicRouteLocator (AC1, AC5)
  - [x] Передача rateLimitId через атрибуты exchange
  - [x] Обновить RouteCacheManager для кэширования rate limit данных
  - [x] Обеспечить refresh rate limits при cache invalidation

- [x] Task 6: Обработка HTTP 429 ответа (AC2)
  - [x] RFC 7807 формат для 429 ответа
  - [x] Включение correlationId в error response
  - [x] Правильные заголовки Retry-After и X-RateLimit-*

- [x] Task 7: Unit и Integration тесты (AC1-AC7)
  - [x] Unit тесты для Token Bucket логики
  - [x] Unit тесты для RateLimitFilter
  - [x] Integration тесты с Testcontainers (Redis + PostgreSQL)
  - [x] Тест graceful degradation при отключении Redis
  - [x] Тест distributed rate limiting с несколькими экземплярами

## Dev Notes

### Архитектура Rate Limiting

```
Request → CorrelationIdFilter → RateLimitFilter → DynamicRouteLocator → Upstream
              (order: 0)        (order: 10)           (routing)
                                     ↓
                                  Redis
                              (token bucket)
                                     ↓
                             Caffeine (fallback)
```

### Существующие компоненты для reuse

**CorrelationIdFilter.kt** — паттерн GlobalFilter:
```kotlin
@Component
class RateLimitFilter(
    private val rateLimitService: RateLimitService,
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val caffeineCache: Cache<String, RateLimitTokenBucket>
) : GlobalFilter, Ordered {

    override fun getOrder(): Int = 10 // После CorrelationIdFilter (0), до LoggingFilter (LOWEST - 1)

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        // ...
    }
}
```

**RouteCacheManager.kt** — кэширование маршрутов:
- Используй аналогичный паттерн AtomicReference + Caffeine
- Кэшируй rate limit данные вместе с маршрутами

**DynamicRouteLocator.kt** — роутинг:
- Добавь передачу rateLimitId через exchange attributes

### Token Bucket Algorithm (Redis Lua Script)

```lua
-- Ключ: ratelimit:{routeId}:{clientKey}
-- KEYS[1] = key для rate limit данных
-- ARGV[1] = requestsPerSecond (rate)
-- ARGV[2] = burstSize (capacity)
-- ARGV[3] = текущее время (unix timestamp с миллисекундами)
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
local refill = math.floor(elapsed * rate)
tokens = math.min(capacity, tokens + refill)
lastRefill = now

-- Проверяем доступность токена
local allowed = 0
if tokens >= 1 then
    tokens = tokens - 1
    allowed = 1
end

-- Сохраняем состояние
redis.call('HSET', key, 'tokens', tokens, 'lastRefill', lastRefill)
redis.call('EXPIRE', key, ttl)

-- Рассчитываем время reset
local resetTime = now + math.ceil((capacity - tokens) / rate * 1000)

return {allowed, tokens, resetTime}
```

### Структура ключей Redis

| Ключ | Описание | TTL |
|------|----------|-----|
| `ratelimit:{routeId}:{clientIp}` | Token bucket состояние | burstSize / requestsPerSecond * 2 |

**Client Identifier Strategy:**
- По умолчанию: IP адрес клиента (X-Forwarded-For или remote address)
- Будущее: API key, user ID (Phase 2)

### RateLimitFilter Implementation Pattern

```kotlin
@Component
class RateLimitFilter(
    private val redisTemplate: ReactiveRedisTemplate<String, String>,
    private val rateLimitCache: Cache<UUID, RateLimitPolicy>,
    @Value("\${gateway.ratelimit.fallback-enabled:true}")
    private val fallbackEnabled: Boolean
) : GlobalFilter, Ordered {

    companion object {
        const val RATE_LIMIT_ATTRIBUTE = "gateway.rateLimit"
        const val ROUTE_ID_ATTRIBUTE = "gateway.routeId"
        private const val FILTER_ORDER = 10
    }

    private val logger = LoggerFactory.getLogger(RateLimitFilter::class.java)
    private val luaScript: RedisScript<List<Long>> = createTokenBucketScript()

    override fun getOrder(): Int = FILTER_ORDER

    override fun filter(exchange: ServerWebExchange, chain: GatewayFilterChain): Mono<Void> {
        // 1. Получаем routeId и rateLimitId из атрибутов exchange
        val routeId = exchange.getAttribute<UUID>(ROUTE_ID_ATTRIBUTE)
        val rateLimit = exchange.getAttribute<RateLimitPolicy>(RATE_LIMIT_ATTRIBUTE)

        // 2. Если нет rate limit — пропускаем
        if (rateLimit == null) {
            return chain.filter(exchange)
        }

        // 3. Получаем client identifier
        val clientKey = extractClientKey(exchange)
        val bucketKey = "ratelimit:${routeId}:${clientKey}"

        // 4. Выполняем token bucket check через Redis
        return checkRateLimit(bucketKey, rateLimit)
            .flatMap { result ->
                if (result.allowed) {
                    // Добавляем заголовки rate limit в успешный ответ
                    addRateLimitHeaders(exchange, rateLimit, result)
                    chain.filter(exchange)
                } else {
                    // Возвращаем 429 Too Many Requests
                    rejectRequest(exchange, rateLimit, result)
                }
            }
            .onErrorResume { ex ->
                // Graceful degradation при ошибке Redis
                logger.warn("Rate limiting disabled: Redis unavailable - {}", ex.message)
                if (fallbackEnabled) {
                    checkLocalRateLimit(bucketKey, rateLimit)
                        .flatMap { result ->
                            if (result.allowed) {
                                chain.filter(exchange)
                            } else {
                                rejectRequest(exchange, rateLimit, result)
                            }
                        }
                } else {
                    // Пропускаем если fallback отключён
                    chain.filter(exchange)
                }
            }
    }

    private fun extractClientKey(exchange: ServerWebExchange): String {
        // Используем паттерн из LoggingFilter
        val forwardedFor = exchange.request.headers.getFirst("X-Forwarded-For")
        if (!forwardedFor.isNullOrBlank()) {
            return forwardedFor.split(",").first().trim()
        }
        return exchange.request.remoteAddress?.address?.hostAddress ?: "unknown"
    }
}
```

### HTTP 429 Response (RFC 7807)

```kotlin
private fun rejectRequest(
    exchange: ServerWebExchange,
    rateLimit: RateLimitPolicy,
    result: RateLimitResult
): Mono<Void> {
    val response = exchange.response
    response.statusCode = HttpStatus.TOO_MANY_REQUESTS

    // Rate limit headers
    response.headers.add("X-RateLimit-Limit", rateLimit.requestsPerSecond.toString())
    response.headers.add("X-RateLimit-Remaining", "0")
    response.headers.add("X-RateLimit-Reset", result.resetTime.toString())

    val retryAfterSeconds = ((result.resetTime - System.currentTimeMillis()) / 1000).coerceAtLeast(1)
    response.headers.add("Retry-After", retryAfterSeconds.toString())
    response.headers.contentType = MediaType.APPLICATION_JSON

    val correlationId = exchange.getAttribute<String>(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE) ?: "unknown"

    val errorBody = """
    {
        "type": "https://api.gateway/errors/rate-limit-exceeded",
        "title": "Too Many Requests",
        "status": 429,
        "detail": "Rate limit exceeded. Try again in $retryAfterSeconds seconds.",
        "correlationId": "$correlationId"
    }
    """.trimIndent()

    val buffer = response.bufferFactory().wrap(errorBody.toByteArray())
    return response.writeWith(Mono.just(buffer))
}
```

### Интеграция с DynamicRouteLocator

Обновить DynamicRouteLocator для передачи rate limit данных через exchange attributes:

```kotlin
// В DynamicRouteLocator.getRoutes()
.map { dbRoute ->
    Route.async()
        .id(dbRoute.id!!.toString())
        .uri(URI.create(dbRoute.upstreamUrl))
        .predicate { exchange ->
            val pathMatches = matchesPrefix(exchange.request.path.value(), dbRoute.path)
            val methodMatches = dbRoute.methods.isEmpty() ||
                dbRoute.methods.any { it.equals(exchange.request.method.name(), ignoreCase = true) }

            if (pathMatches && methodMatches) {
                // Передаём rate limit данные через атрибуты
                exchange.attributes[RateLimitFilter.ROUTE_ID_ATTRIBUTE] = dbRoute.id
                if (dbRoute.rateLimitId != null) {
                    val rateLimit = rateLimitCache.getIfPresent(dbRoute.rateLimitId)
                    rateLimit?.let {
                        exchange.attributes[RateLimitFilter.RATE_LIMIT_ATTRIBUTE] = it
                    }
                }
            }
            pathMatches && methodMatches
        }
        .build()
}
```

### Route Entity с RateLimit

Route entity уже содержит `rateLimitId: UUID?` (добавлено в Story 5.1).

Для эффективного кэширования нужно:
1. Загружать rate limit данные вместе с маршрутами
2. Кэшировать их в отдельном Caffeine cache
3. Обновлять кэш при cache invalidation events

```kotlin
// В RouteCacheManager добавить
private val rateLimitCache = AtomicReference<Map<UUID, RateLimitPolicy>>(emptyMap())

fun refreshCache(): Mono<Void> =
    routeRepository.findByStatus(RouteStatus.PUBLISHED)
        .collectList()
        .flatMap { routes ->
            // Собираем уникальные rateLimitId
            val rateLimitIds = routes.mapNotNull { it.rateLimitId }.distinct()

            // Загружаем все rate limits за один запрос
            rateLimitRepository.findAllById(rateLimitIds)
                .collectMap { it.id!! }
                .map { rateLimits -> Pair(routes, rateLimits) }
        }
        .doOnNext { (routes, rateLimits) ->
            cachedRoutes.set(routes)
            rateLimitCache.set(rateLimits)
            caffeineCache.put(ROUTE_CACHE_KEY, routes)
            eventPublisher.publishEvent(RefreshRoutesEvent(this))
        }
        .then()
```

### Структура файлов для создания

| Файл | Действие |
|------|---------|
| `backend/gateway-core/src/main/kotlin/com/company/gateway/core/filter/RateLimitFilter.kt` | СОЗДАТЬ |
| `backend/gateway-core/src/main/kotlin/com/company/gateway/core/ratelimit/RateLimitService.kt` | СОЗДАТЬ |
| `backend/gateway-core/src/main/kotlin/com/company/gateway/core/ratelimit/TokenBucketScript.kt` | СОЗДАТЬ |
| `backend/gateway-core/src/main/kotlin/com/company/gateway/core/ratelimit/RateLimitResult.kt` | СОЗДАТЬ |
| `backend/gateway-core/src/main/kotlin/com/company/gateway/core/ratelimit/LocalRateLimiter.kt` | СОЗДАТЬ |
| `backend/gateway-core/src/main/kotlin/com/company/gateway/core/repository/RateLimitRepository.kt` | СОЗДАТЬ |
| `backend/gateway-core/src/main/kotlin/com/company/gateway/core/cache/RouteCacheManager.kt` | ИЗМЕНИТЬ — добавить rate limit кэширование |
| `backend/gateway-core/src/main/kotlin/com/company/gateway/core/route/DynamicRouteLocator.kt` | ИЗМЕНИТЬ — передача rate limit атрибутов |
| `backend/gateway-core/src/main/kotlin/com/company/gateway/core/cache/CacheConfig.kt` | ИЗМЕНИТЬ — добавить rate limit cache |
| `backend/gateway-core/src/main/resources/scripts/token-bucket.lua` | СОЗДАТЬ |
| `backend/gateway-core/src/test/kotlin/com/company/gateway/core/filter/RateLimitFilterTest.kt` | СОЗДАТЬ |
| `backend/gateway-core/src/test/kotlin/com/company/gateway/core/ratelimit/TokenBucketScriptTest.kt` | СОЗДАТЬ |
| `backend/gateway-core/src/test/kotlin/com/company/gateway/core/integration/RateLimitIntegrationTest.kt` | СОЗДАТЬ |

### Project Structure Notes

```
backend/gateway-core/src/main/kotlin/com/company/gateway/core/
├── filter/
│   ├── CorrelationIdFilter.kt    # Существует (order: HIGHEST_PRECEDENCE)
│   ├── LoggingFilter.kt          # Существует (order: LOWEST_PRECEDENCE - 1)
│   └── RateLimitFilter.kt        # СОЗДАТЬ (order: 10)
├── ratelimit/
│   ├── RateLimitService.kt       # СОЗДАТЬ — координация Redis + fallback
│   ├── TokenBucketScript.kt      # СОЗДАТЬ — Redis Lua script wrapper
│   ├── RateLimitResult.kt        # СОЗДАТЬ — DTO для результата проверки
│   └── LocalRateLimiter.kt       # СОЗДАТЬ — Caffeine fallback
├── repository/
│   ├── RouteRepository.kt        # Существует
│   └── RateLimitRepository.kt    # СОЗДАТЬ — R2DBC для rate_limits
├── cache/
│   ├── CacheConfig.kt            # ИЗМЕНИТЬ — добавить rate limit cache bean
│   └── RouteCacheManager.kt      # ИЗМЕНИТЬ — кэширование rate limits
└── route/
    └── DynamicRouteLocator.kt    # ИЗМЕНИТЬ — передача атрибутов

backend/gateway-core/src/main/resources/
└── scripts/
    └── token-bucket.lua          # СОЗДАТЬ — Redis Lua script

backend/gateway-core/src/test/kotlin/com/company/gateway/core/
├── filter/
│   └── RateLimitFilterTest.kt    # СОЗДАТЬ
├── ratelimit/
│   └── TokenBucketScriptTest.kt  # СОЗДАТЬ
└── integration/
    └── RateLimitIntegrationTest.kt  # СОЗДАТЬ
```

### Конфигурация application.yml

```yaml
gateway:
  ratelimit:
    fallback-enabled: true           # Включить Caffeine fallback
    fallback-reduction: 0.5          # 50% от policy при fallback
    redis-key-prefix: "ratelimit"
    default-ttl-seconds: 120         # TTL для Redis ключей

  cache:
    ttl-seconds: 60
    max-routes: 1000
    max-rate-limits: 100             # ДОБАВИТЬ
```

### Архитектурные требования

- **Reactive patterns**: Mono/Flux chains, без .block()
- **GlobalFilter ordering**: RateLimitFilter после CorrelationIdFilter (order: 10)
- **Redis Lua scripts**: атомарные операции для token bucket
- **Graceful degradation**: Caffeine fallback при недоступности Redis (NFR7)
- **RFC 7807**: для 429 Too Many Requests ответов
- **Distributed**: все инстансы gateway используют общий Redis
- **Комментарии**: только на русском языке
- **Названия тестов**: только на русском языке
- **Testcontainers**: для интеграционных тестов (Redis + PostgreSQL)

### Тесты на русском языке (примеры)

```kotlin
@Test
fun `запросы в пределах лимита проходят успешно`() { ... }

@Test
fun `превышение burst возвращает 429`() { ... }

@Test
fun `rate limit заголовки добавляются в ответ`() { ... }

@Test
fun `маршрут без rate limit проходит без проверки`() { ... }

@Test
fun `при недоступности Redis используется fallback`() { ... }

@Test
fun `distributed rate limiting через несколько инстансов`() { ... }
```

### Важные паттерны из предыдущих stories

1. **GlobalFilter pattern**: использовать как в CorrelationIdFilter и LoggingFilter
2. **Caffeine cache**: паттерн из RouteCacheManager для fallback
3. **Reactive chains**: всегда Mono/Flux, без .block()
4. **Error handling**: onErrorResume для graceful degradation
5. **Logging**: MDC для structured logging с correlationId
6. **Exchange attributes**: передача данных между фильтрами

### References

- [Source: planning-artifacts/epics.md#Story-5.3] — Story requirements и AC
- [Source: planning-artifacts/architecture.md#Data-Architecture] — Redis, Caffeine, graceful degradation
- [Source: planning-artifacts/architecture.md#Infrastructure] — NFR7 graceful degradation
- [Source: implementation-artifacts/5-1-rate-limit-policy-crud-api.md] — RateLimit entity, policy structure
- [Source: implementation-artifacts/5-2-assign-rate-limit-route-api.md] — Route + RateLimit связь
- [Source: backend/gateway-core/src/main/kotlin/com/company/gateway/core/filter/CorrelationIdFilter.kt] — GlobalFilter паттерн
- [Source: backend/gateway-core/src/main/kotlin/com/company/gateway/core/filter/LoggingFilter.kt] — GlobalFilter паттерн, MDC
- [Source: backend/gateway-core/src/main/kotlin/com/company/gateway/core/cache/RouteCacheManager.kt] — кэширование паттерн
- [Source: backend/gateway-core/src/main/kotlin/com/company/gateway/core/route/DynamicRouteLocator.kt] — роутинг, exchange attributes
- [Source: backend/gateway-core/src/main/kotlin/com/company/gateway/core/cache/CacheConfig.kt] — Caffeine + Redis config
- [Source: backend/gateway-common/src/main/kotlin/com/company/gateway/common/model/Route.kt] — entity с rateLimitId
- [Source: backend/gateway-common/src/main/kotlin/com/company/gateway/common/model/RateLimit.kt] — RateLimit entity

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- Все unit и integration тесты проходят
- Интеграционные тесты используют Testcontainers (PostgreSQL + Redis)

### Completion Notes List

- **Task 1**: Создан RateLimitRepository и обновлён RouteCacheManager для batch-загрузки rate limit политик
- **Task 2**: Реализован RateLimitFilter (GlobalFilter, order: 10) с проверкой rate limit через атрибуты exchange
- **Task 3**: Реализован Token Bucket через Redis Lua script (атомарные операции, distributed rate limiting)
- **Task 4**: Реализован LocalRateLimiter (Caffeine fallback) с консервативными лимитами (50%)
- **Task 5**: DynamicRouteLocator обновлён для передачи routeId и rateLimit через exchange attributes
- **Task 6**: HTTP 429 ответ в RFC 7807 формате с correlationId и заголовками Retry-After, X-RateLimit-*
- **Task 7**: Unit тесты (RateLimitFilterTest, LocalRateLimiterTest) и интеграционные тесты (RateLimitIntegrationTest)

### Implementation Plan

Rate limiting реализован по архитектуре token bucket:
1. RateLimitFilter перехватывает запросы после CorrelationIdFilter
2. Проверяет наличие rateLimit атрибута (устанавливается в DynamicRouteLocator)
3. Выполняет атомарную проверку через Redis Lua script
4. При ошибке Redis переключается на локальный fallback с 50% лимитами
5. Добавляет X-RateLimit-* заголовки в успешные ответы
6. Возвращает HTTP 429 RFC 7807 при превышении лимита

### File List

**Новые файлы:**
- backend/gateway-core/src/main/kotlin/com/company/gateway/core/repository/RateLimitRepository.kt
- backend/gateway-core/src/main/kotlin/com/company/gateway/core/filter/RateLimitFilter.kt
- backend/gateway-core/src/main/kotlin/com/company/gateway/core/ratelimit/RateLimitResult.kt
- backend/gateway-core/src/main/kotlin/com/company/gateway/core/ratelimit/RateLimitService.kt
- backend/gateway-core/src/main/kotlin/com/company/gateway/core/ratelimit/TokenBucketScript.kt
- backend/gateway-core/src/main/kotlin/com/company/gateway/core/ratelimit/LocalRateLimiter.kt
- backend/gateway-core/src/main/resources/scripts/token-bucket.lua
- backend/gateway-core/src/test/kotlin/com/company/gateway/core/filter/RateLimitFilterTest.kt
- backend/gateway-core/src/test/kotlin/com/company/gateway/core/ratelimit/LocalRateLimiterTest.kt
- backend/gateway-core/src/test/kotlin/com/company/gateway/core/ratelimit/RateLimitServiceTest.kt (добавлен в code review)
- backend/gateway-core/src/test/kotlin/com/company/gateway/core/ratelimit/TokenBucketScriptTest.kt (добавлен в code review)
- backend/gateway-core/src/test/kotlin/com/company/gateway/core/integration/RateLimitIntegrationTest.kt
- backend/gateway-core/src/test/resources/db/migration/V7__create_rate_limits.sql
- backend/gateway-core/src/test/resources/db/migration/V8__add_rate_limit_to_routes.sql

**Изменённые файлы:**
- backend/gateway-core/src/main/kotlin/com/company/gateway/core/cache/CacheConfig.kt
- backend/gateway-core/src/main/kotlin/com/company/gateway/core/cache/RouteCacheManager.kt
- backend/gateway-core/src/main/kotlin/com/company/gateway/core/route/DynamicRouteLocator.kt
- backend/gateway-core/src/main/resources/application.yml
- backend/gateway-core/src/test/kotlin/com/company/gateway/core/cache/RouteCacheManagerTest.kt
- backend/gateway-core/src/test/resources/db/migration/V6__add_approval_fields.sql

## Change Log

- 2026-02-19: Реализован Rate Limiting Filter с distributed Redis и локальным Caffeine fallback (Story 5.3)
- 2026-02-19: Code Review — добавлены недостающие тесты для AC3, AC4, AC6; добавлены unit тесты RateLimitServiceTest и TokenBucketScriptTest
