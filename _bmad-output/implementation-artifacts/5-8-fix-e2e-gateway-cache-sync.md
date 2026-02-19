# Story 5.8: Fix E2E Gateway Cache Sync

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **QA Engineer**,
I want the Gateway cache to sync immediately when routes/policies are created,
so that E2E test AC3 (Rate limiting применяется в Gateway) passes reliably.

## Problem Statement

Текущая проблема: E2E тест AC3 в epic-5.spec.ts пропущен (`test.skip`) из-за того, что gateway-core не получает cache invalidation события вовремя. При создании маршрута с rate limit политикой gateway-core должен получить событие через Redis pub/sub и обновить свой кэш, но этого не происходит надёжно.

**Root Cause Analysis:**

1. **Rate limit политика не загружена в кэш маршрутов**: `RouteCacheManager.refreshCache()` загружает маршруты и batch-загружает политики, но если `rateLimitRepository.findAllByIdIn()` вернёт partial результат, политика останется null в кэше.

2. **DynamicRouteLocator молча игнорирует missing policies**: На lines 56-60 если `cacheManager.getCachedRateLimit(rateLimitId)` вернёт null, атрибут `RATE_LIMIT_ATTRIBUTE` не устанавливается, и `RateLimitFilter` пропускает проверку.

3. **Нет явного события при создании/изменении rate limit политики**: `RouteEventPublisher` публикует события только для маршрутов, но не для политик. Если политика создана после маршрута, кэш не обновится.

4. **Race condition**: E2E тест создаёт политику, затем маршрут, затем approve — всё быстро. Gateway может не успеть загрузить политику при refresh.

## Acceptance Criteria

**AC1 — Rate limit политики синхронизируются немедленно:**

**Given** Admin создал rate limit политику через API
**When** gateway-admin публикует событие в Redis
**Then** gateway-core обновляет кэш политик
**And** политика доступна для RateLimitFilter в течение 5 секунд

**AC2 — Маршруты с rate limit работают сразу после publish:**

**Given** Developer создал маршрут с rate limit и он был approved
**When** первый запрос приходит на gateway-core
**Then** rate limit применяется
**And** заголовки X-RateLimit-* присутствуют в ответе

**AC3 — E2E тест "Rate limiting применяется в Gateway" проходит:**

**Given** E2E тест epic-5.spec.ts AC3 запущен
**When** тест создаёт политику, маршрут и отправляет запросы
**Then** тест проходит без skip
**And** HTTP 429 возвращается при превышении лимита

## Tasks / Subtasks

- [x] Task 1: Добавить Redis pub/sub для rate limit политик (AC1)
  - [x] Создать `RateLimitEventPublisher.kt` в gateway-admin
  - [x] Публиковать событие при CREATE/UPDATE/DELETE политики
  - [x] Добавить подписку в `RouteRefreshService.kt` на канал `ratelimit-cache-invalidation`
  - [x] При получении события вызывать `cacheManager.refreshRateLimitCache()`

- [x] Task 2: Добавить явную загрузку политики в RouteCacheManager (AC2)
  - [x] Метод `refreshRateLimitCache(rateLimitId: UUID)` — загрузить одну политику
  - [x] Fallback: при missing policy загрузить из БД напрямую
  - [x] Логировать warning если политика не найдена

- [x] Task 3: Исправить DynamicRouteLocator для fallback загрузки (AC2)
  - [x] Если `getCachedRateLimit(id)` вернул null, попробовать загрузить напрямую
  - [x] Логировать warning при fallback загрузке
  - [x] Кэшировать загруженную политику для следующих запросов

- [x] Task 4: Включить E2E тест AC3 и проверить (AC3)
  - [x] Убрать `test.skip` с теста "Rate limiting применяется в Gateway"
  - [x] Добавить retry/wait логику если нужно
  - [x] Исправлен blocker: удалён RedisConfig.kt — Spring Boot auto-configuration сама создаёт ReactiveStringRedisTemplate
  - [x] Redis pub/sub работает, кэш синхронизируется, predicate срабатывает корректно
  - [x] Запустить тест и убедиться что проходит
  - [x] Исправлен blocker Story 5.10: RewritePath filter в DynamicRouteLocator (OrderedGatewayFilter order=10001)

- [x] Task 5: Unit/Integration тесты для новой функциональности
  - [x] Тест для RateLimitEventPublisher
  - [x] Тест для RouteRefreshService подписки на ratelimit channel
  - [x] Тест для DynamicRouteLocator fallback загрузки

## Dev Notes

### Текущая архитектура кэширования

**Двухуровневое кэширование в gateway-core:**

```
1. AtomicReference<List<Route>> — in-memory, мгновенный доступ
2. Caffeine cache — TTL 60 секунд, fallback при недоступности Redis
```

**RouteCacheManager.kt** (`gateway-core/src/main/kotlin/.../cache/RouteCacheManager.kt`, 136 строк):
- `initializeCache()` — инициализация при старте (@EventListener ApplicationReadyEvent)
- `refreshCache()` — обновляет маршруты и rate limits из БД
- `getCachedRoutes()` — возвращает список маршрутов
- `getCachedRateLimit(id: UUID)` — возвращает политику по ID

**RouteRefreshService.kt** (`gateway-core/src/main/kotlin/.../route/RouteRefreshService.kt`, 117 строк):
- Подписка на Redis канал `route-cache-invalidation`
- При получении события вызывает `cacheManager.refreshCache()`
- Graceful degradation: при недоступности Redis откатывается на Caffeine TTL
- Reconnect механизм каждые 30 секунд

**DynamicRouteLocator.kt** (`gateway-core/src/main/kotlin/.../route/DynamicRouteLocator.kt`, 86 строк):
- Реализует Spring Cloud Gateway `RouteLocator`
- Устанавливает атрибуты `ROUTE_ID_ATTRIBUTE` и `RATE_LIMIT_ATTRIBUTE`
- **Проблема lines 56-60**: Если политика не найдена в кэше, атрибут не устанавливается

### Ключевой код для изменения

**1. НОВЫЙ ФАЙЛ: RateLimitEventPublisher.kt** (gateway-admin):

```kotlin
package com.company.gateway.admin.publisher

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Публикует события изменения rate limit политик в Redis для синхронизации gateway-core.
 */
@Service
class RateLimitEventPublisher(
    @Autowired(required = false)
    private val redisTemplate: ReactiveStringRedisTemplate?
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val RATELIMIT_CACHE_CHANNEL = "ratelimit-cache-invalidation"
    }

    /**
     * Публикует событие изменения политики rate limit.
     * При недоступности Redis событие пропускается с warning.
     */
    fun publishRateLimitChanged(rateLimitId: UUID): Mono<Void> {
        return redisTemplate?.convertAndSend(RATELIMIT_CACHE_CHANNEL, rateLimitId.toString())
            ?.doOnSuccess {
                logger.info("Опубликовано событие изменения политики: $rateLimitId")
            }
            ?.then()
            ?: Mono.empty<Void>().also {
                logger.warn("Redis недоступен, событие rate limit не опубликовано: $rateLimitId")
            }
    }
}
```

**2. ИЗМЕНЕНИЕ: RateLimitService.kt** (gateway-admin) — вызов publisher:

```kotlin
// В методе create():
fun create(request: CreateRateLimitRequest): Mono<RateLimit> =
    rateLimitRepository.save(RateLimit(...))
        .flatMap { saved ->
            rateLimitEventPublisher.publishRateLimitChanged(saved.id!!)
                .thenReturn(saved)
        }

// В методе update():
fun update(id: UUID, request: UpdateRateLimitRequest): Mono<RateLimit> =
    rateLimitRepository.findById(id)
        .flatMap { existing ->
            rateLimitRepository.save(existing.copy(...))
        }
        .flatMap { updated ->
            rateLimitEventPublisher.publishRateLimitChanged(updated.id!!)
                .thenReturn(updated)
        }

// В методе delete():
fun delete(id: UUID): Mono<Void> =
    rateLimitRepository.deleteById(id)
        .then(rateLimitEventPublisher.publishRateLimitChanged(id))
```

**3. ИЗМЕНЕНИЕ: RouteRefreshService.kt** (gateway-core) — подписка на оба канала:

```kotlin
companion object {
    const val ROUTE_CACHE_CHANNEL = "route-cache-invalidation"
    const val RATELIMIT_CACHE_CHANNEL = "ratelimit-cache-invalidation"  // НОВОЕ
}

@EventListener(ApplicationReadyEvent::class)
fun initializeSubscriptions() {
    subscribeToRouteInvalidationEvents()
    subscribeToRateLimitInvalidationEvents()  // НОВОЕ
}

private fun subscribeToRateLimitInvalidationEvents() {
    val container = RedisMessageListenerContainer(connectionFactory)
    container.receive(ChannelTopic(RATELIMIT_CACHE_CHANNEL))
        .doOnNext { message ->
            val rateLimitId = UUID.fromString(message.message)
            logger.info("Получено событие инвалидации rate limit: $rateLimitId")
            cacheManager.refreshRateLimitCache(rateLimitId).subscribe()
        }
        .doOnError { e ->
            logger.warn("Ошибка подписки на rate limit events: ${e.message}")
            scheduleReconnect()
        }
        .subscribe()

    logger.info("Подписка на $RATELIMIT_CACHE_CHANNEL активирована")
}
```

**4. ИЗМЕНЕНИЕ: RouteCacheManager.kt** (gateway-core) — метод refreshRateLimitCache:

```kotlin
/**
 * Загружает одну политику rate limit по ID и обновляет кэш.
 * Используется при получении события из Redis pub/sub.
 */
fun refreshRateLimitCache(rateLimitId: UUID): Mono<Void> =
    rateLimitRepository.findById(rateLimitId)
        .doOnNext { rateLimit ->
            // Обновляем AtomicReference map
            cachedRateLimits.updateAndGet { currentMap ->
                currentMap.toMutableMap().apply {
                    put(rateLimitId, rateLimit)
                }
            }
            // Обновляем Caffeine кэш
            caffeineRateLimitCache.put(rateLimitId, rateLimit)
            logger.info("Политика rate limit обновлена в кэше: $rateLimitId")
        }
        .doOnError { e ->
            logger.warn("Ошибка загрузки политики $rateLimitId: ${e.message}")
        }
        .switchIfEmpty(
            Mono.fromRunnable {
                // Политика удалена — удаляем из кэша
                cachedRateLimits.updateAndGet { currentMap ->
                    currentMap.toMutableMap().apply { remove(rateLimitId) }
                }
                caffeineRateLimitCache.invalidate(rateLimitId)
                logger.info("Политика rate limit удалена из кэша: $rateLimitId")
            }
        )
        .then()

/**
 * Синхронная загрузка политики — fallback для DynamicRouteLocator.
 * ВАЖНО: Блокирующий вызов, использовать только для fallback!
 */
fun loadRateLimitSync(rateLimitId: UUID): RateLimit? {
    return try {
        rateLimitRepository.findById(rateLimitId)
            .doOnNext { rateLimit ->
                // Кэшируем загруженную политику
                cachedRateLimits.updateAndGet { map ->
                    map.toMutableMap().apply { put(rateLimitId, rateLimit) }
                }
                caffeineRateLimitCache.put(rateLimitId, rateLimit)
            }
            .block(Duration.ofSeconds(5))
    } catch (e: Exception) {
        logger.error("Ошибка синхронной загрузки политики $rateLimitId: ${e.message}")
        null
    }
}
```

**5. ИЗМЕНЕНИЕ: DynamicRouteLocator.kt** (gateway-core) — fallback загрузка:

```kotlin
// Изменить lines 56-60
dbRoute.rateLimitId?.let { rateLimitId ->
    var rateLimit = cacheManager.getCachedRateLimit(rateLimitId)

    // Fallback: если политика не в кэше, загрузить напрямую
    if (rateLimit == null) {
        logger.warn("Политика $rateLimitId не найдена в кэше, выполняем fallback загрузку")
        rateLimit = cacheManager.loadRateLimitSync(rateLimitId)
    }

    rateLimit?.let {
        exchange.attributes[RateLimitFilter.RATE_LIMIT_ATTRIBUTE] = it
    } ?: logger.warn("Политика $rateLimitId не найдена даже при fallback, rate limiting отключён для маршрута ${dbRoute.id}")
}
```

### Конфигурация Redis channels

```yaml
# application.yml (gateway-core)
gateway:
  cache:
    route-invalidation-channel: route-cache-invalidation
    ratelimit-invalidation-channel: ratelimit-cache-invalidation  # НОВОЕ
    ttl-seconds: 60
    max-routes: 1000
    max-rate-limits: 100
    reconnect-delay-seconds: 30
```

### E2E тест AC3 — что нужно исправить

**Файл:** `frontend/admin-ui/e2e/epic-5.spec.ts` (lines 259-299)

**Текущий код (SKIPPED):**
```typescript
test.skip('Rate limiting применяется в Gateway', async ({ page }) => {
    // TODO: Тест требует работающего Redis pub/sub
    // ...
})
```

**Изменения:**
1. Убрать `test.skip` → `test`
2. Добавить wait после создания политики для синхронизации
3. Проверить X-RateLimit-* заголовки
4. Проверить HTTP 429 при превышении

**Пример кода теста:**
```typescript
test('Rate limiting применяется в Gateway', async ({ page }) => {
    const TIMESTAMP = Date.now()
    const GATEWAY_URL = 'http://localhost:8080'

    // Setup: Admin создаёт политику через API
    await login(page, 'test-admin', 'Test1234!', '/rate-limits')

    const policyId = await createRateLimitPolicy(
        page,
        `e2e-gateway-${TIMESTAMP}`,
        5,  // 5 req/sec
        10  // burst 10
    )

    // Admin создаёт и публикует маршрут с политикой
    const routeId = await createPublishedRouteWithRateLimit(
        page,
        `gateway-${TIMESTAMP}`,
        policyId
    )

    // Ждём синхронизации кэша (Redis pub/sub + обработка)
    await page.waitForTimeout(2000)

    // Проверяем что rate limit применяется
    const response = await page.request.get(
        `${GATEWAY_URL}/e2e-rl-gateway-${TIMESTAMP}`,
        { failOnStatusCode: false }
    )

    expect(response.ok()).toBeTruthy()
    expect(response.headers()['x-ratelimit-limit']).toBe('5')
    expect(Number(response.headers()['x-ratelimit-remaining'])).toBeGreaterThanOrEqual(0)

    // Превышаем лимит (отправляем 15 запросов)
    for (let i = 0; i < 15; i++) {
        await page.request.get(
            `${GATEWAY_URL}/e2e-rl-gateway-${TIMESTAMP}`,
            { failOnStatusCode: false }
        )
    }

    // Следующий запрос должен вернуть 429
    const overLimitResponse = await page.request.get(
        `${GATEWAY_URL}/e2e-rl-gateway-${TIMESTAMP}`,
        { failOnStatusCode: false }
    )

    expect(overLimitResponse.status()).toBe(429)
    expect(overLimitResponse.headers()['retry-after']).toBeTruthy()

    // Cleanup
    await deleteRateLimitPolicy(page, policyId)
})
```

### Тестирование

**Unit тесты:**
- `RateLimitEventPublisherTest.kt` — публикация событий в Redis
- `RouteCacheManagerTest.kt` — загрузка и обновление кэша политик

**Integration тесты:**
- `RateLimitCacheIntegrationTest.kt` — полный цикл: создание политики → pub/sub → кэш обновлён
- Использовать Testcontainers для Redis

**E2E тесты:**
- `epic-5.spec.ts` AC3 — весь flow от создания до применения rate limit

### Project Structure Notes

**Новые файлы:**
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/publisher/RateLimitEventPublisher.kt`
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/publisher/RateLimitEventPublisherTest.kt`

**Изменяемые файлы:**
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/route/RouteRefreshService.kt`
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/cache/RouteCacheManager.kt`
- `backend/gateway-core/src/main/kotlin/com/company/gateway/core/route/DynamicRouteLocator.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/RateLimitService.kt`
- `frontend/admin-ui/e2e/epic-5.spec.ts` (убрать skip с AC3)

### References

- [Source: gateway-core/src/main/kotlin/.../cache/RouteCacheManager.kt] — текущая логика кэширования
- [Source: gateway-core/src/main/kotlin/.../route/RouteRefreshService.kt:1-117] — подписка на Redis pub/sub
- [Source: gateway-core/src/main/kotlin/.../route/DynamicRouteLocator.kt:56-60] — установка атрибутов (проблемный код)
- [Source: gateway-admin/src/main/kotlin/.../publisher/RouteEventPublisher.kt] — пример publisher для маршрутов
- [Source: frontend/admin-ui/e2e/epic-5.spec.ts:259-299] — E2E тест AC3 (skipped)
- [Source: implementation-artifacts/5-6-e2e-playwright-happy-path.md] — контекст проблемы AC3
- [Source: implementation-artifacts/5-7-e2e-infrastructure-improvements.md] — предыдущая история

### Git Context

**Последние коммиты Epic 5:**
```
4c3a355 fix: correct Story 5.6 status — Tasks 4,5 marked incomplete (tests skipped)
daf68a5 feat: implement Story 5.7 — E2E Infrastructure Improvements
911d36c feat: add Stories 5-7, 5-8, 5-9 for E2E test improvements (Epic 5)
648e509 fix: add rateLimitId support to CreateRouteRequest (Story 5.5/5.6)
d0759f4 fix: enable all E2E tests and add data-testid for Story 5.6
```

**Паттерн коммита:** `feat: implement Story 5.8 — Fix E2E Gateway Cache Sync`

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List

