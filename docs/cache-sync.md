# Redis Pub/Sub — Синхронизация кэша маршрутов

Эта документация описывает механизм синхронизации кэша между gateway-admin и gateway-core через Redis Pub/Sub.

## Содержание

- [Введение](#введение)
- [Архитектура](#архитектура)
- [Каналы и формат сообщений](#каналы-и-формат-сообщений)
- [Компоненты gateway-admin (Publishers)](#компоненты-gateway-admin-publishers)
- [Компоненты gateway-core (Subscribers)](#компоненты-gateway-core-subscribers)
- [Fallback механизм](#fallback-механизм)
- [Troubleshooting & Debugging](#troubleshooting--debugging)

---

## Введение

### Зачем нужна синхронизация кэша?

API Gateway состоит из двух сервисов:
- **gateway-admin** — управление маршрутами, пользователями, политиками (CRUD операции)
- **gateway-core** — проксирование запросов на upstream сервисы (runtime)

Проблема: когда администратор одобряет маршрут в gateway-admin, как gateway-core узнаёт об этом изменении?

```
┌─────────────────┐                         ┌─────────────────┐
│  gateway-admin  │   ??? Как передать? ??? │  gateway-core   │
│                 │                         │                 │
│  PostgreSQL DB  │───────────────────────► │  In-Memory Cache│
│  (routes table) │                         │  (для скорости) │
└─────────────────┘                         └─────────────────┘
```

### Решения и их недостатки

| Подход | Недостаток |
|--------|------------|
| Polling DB | Постоянная нагрузка на PostgreSQL, задержка до интервала опроса |
| Shared cache | Сложная конфигурация, single point of failure |
| HTTP callback | gateway-admin должен знать адреса всех инстансов gateway-core |

### Выбранное решение: Redis Pub/Sub

Redis Pub/Sub обеспечивает:
1. **Мгновенную доставку** — подписчики получают сообщения в реальном времени
2. **Broadcast** — одно сообщение доходит до всех подписанных инстансов
3. **Отсутствие связности** — publisher не знает о subscribers, и наоборот
4. **Отказоустойчивость** — при недоступности Redis используется fallback

---

## Архитектура

### Общая схема

```
                    ┌─────────────────────────────────────┐
                    │              Redis                   │
                    │                                      │
                    │  ┌─────────────────────────────────┐│
                    │  │  channel: route-cache-invalidation ││
                    │  │  channel: ratelimit-cache-invalidation ││
                    │  └─────────────────────────────────┘│
                    └──────────▲──────────────┬───────────┘
                               │              │
                         PUBLISH          SUBSCRIBE
                               │              │
┌──────────────────────────────┴──┐    ┌──────┴──────────────────────────┐
│         gateway-admin           │    │         gateway-core             │
│                                 │    │                                  │
│  ┌─────────────────────────┐   │    │   ┌──────────────────────────┐  │
│  │    ApprovalService      │   │    │   │   RouteRefreshService    │  │
│  │    ───────────────      │   │    │   │   ────────────────────   │  │
│  │    approve(routeId)     │   │    │   │   @EventListener         │  │
│  │           │             │   │    │   │   subscribeToInvalidation│  │
│  │           ▼             │   │    │   │           │              │  │
│  │  RouteEventPublisher    │───┼────┼──►│   receive(message)       │  │
│  │  publishRouteChanged()  │   │    │   │           │              │  │
│  └─────────────────────────┘   │    │   │           ▼              │  │
│                                 │    │   │   RouteCacheManager      │  │
│  ┌─────────────────────────┐   │    │   │   refreshCache()         │  │
│  │    RateLimitService     │   │    │   └──────────────────────────┘  │
│  │    ────────────────     │   │    │                                  │
│  │    create/update/delete │   │    │   ┌──────────────────────────┐  │
│  │           │             │   │    │   │   DynamicRouteLocator    │  │
│  │           ▼             │   │    │   │   ───────────────────    │  │
│  │  RateLimitEventPublisher│───┼────┼──►│   getRoutes()            │  │
│  │  publishRateLimitChanged│   │    │   │   getCachedRoutes()      │  │
│  └─────────────────────────┘   │    │   └──────────────────────────┘  │
│                                 │    │                                  │
└─────────────────────────────────┘    └──────────────────────────────────┘
```

### Последовательность событий

1. Администратор одобряет маршрут в Admin UI
2. `ApprovalService.approve()` меняет статус маршрута на PUBLISHED
3. `RouteEventPublisher.publishRouteChanged(routeId)` отправляет UUID в Redis
4. Redis доставляет сообщение всем подписчикам канала
5. `RouteRefreshService` получает сообщение
6. `RouteCacheManager.refreshCache()` загружает актуальные маршруты из PostgreSQL
7. `RefreshRoutesEvent` уведомляет Spring Cloud Gateway
8. Новый маршрут доступен для проксирования

### Временные характеристики

| Этап | Время |
|------|-------|
| Публикация в Redis | < 1 мс |
| Доставка подписчикам | < 1 мс |
| Загрузка из PostgreSQL | ~10-50 мс |
| **Общее время применения** | **< 100 мс** |

NFR3 гарантирует: изменения применяются в течение 5 секунд (с большим запасом).

---

## Каналы и формат сообщений

### Канал route-cache-invalidation

**Назначение:** Уведомление об изменении маршрутов

| Свойство | Значение |
|----------|----------|
| Название | `route-cache-invalidation` |
| Конфигурация | `gateway.cache.invalidation-channel` |
| Формат сообщения | UUID строка |
| Пример | `550e8400-e29b-41d4-a716-446655440000` |

**Триггеры публикации:**
- Одобрение маршрута (PENDING → PUBLISHED)
- Отклонение маршрута
- Откат маршрута (rollback)

### Канал ratelimit-cache-invalidation

**Назначение:** Уведомление об изменении политик rate limiting

| Свойство | Значение |
|----------|----------|
| Название | `ratelimit-cache-invalidation` |
| Конфигурация | `gateway.cache.ratelimit-invalidation-channel` |
| Формат сообщения | UUID строка |
| Пример | `123e4567-e89b-12d3-a456-426614174000` |

**Триггеры публикации:**
- Создание политики rate limit
- Обновление политики rate limit
- Удаление политики rate limit

### Формат сообщения

Сообщение — простая строка с UUID ресурса, который изменился:

```
route-cache-invalidation: "550e8400-e29b-41d4-a716-446655440000"
ratelimit-cache-invalidation: "123e4567-e89b-12d3-a456-426614174000"
```

**Почему UUID, а не полный объект?**
1. Минимальный размер сообщения
2. Subscriber всё равно загружает актуальные данные из PostgreSQL
3. Нет риска рассинхронизации между сообщением и базой

---

## Компоненты gateway-admin (Publishers)

### RouteEventPublisher

**Путь:** `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/publisher/RouteEventPublisher.kt`

**Назначение:** Публикует события изменения маршрутов в Redis

```kotlin
@Component
class RouteEventPublisher(
    @Autowired(required = false)  // Опционально — работает без Redis
    private val redisTemplate: ReactiveStringRedisTemplate?
) {
    companion object {
        const val ROUTE_CACHE_CHANNEL = "route-cache-invalidation"
    }

    fun publishRouteChanged(routeId: UUID): Mono<Long> {
        if (redisTemplate == null) {
            logger.warn("Redis недоступен — cache invalidation не опубликован: routeId={}", routeId)
            return Mono.just(0L)
        }

        return redisTemplate.convertAndSend(ROUTE_CACHE_CHANNEL, routeId.toString())
            .doOnSuccess { subscribersCount ->
                logger.info("Cache invalidation опубликован: routeId={}, subscribers={}",
                    routeId, subscribersCount)
            }
            .doOnError { error ->
                logger.error("Ошибка публикации cache invalidation: routeId={}, error={}",
                    routeId, error.message)
            }
    }
}
```

**Ключевые особенности:**

1. **`@Autowired(required = false)`** — позволяет работать без Redis (в тестах)
2. **Возвращает `Mono<Long>`** — количество подписчиков, получивших сообщение
3. **Graceful degradation** — при недоступности Redis логирует warning, не падает

### RateLimitEventPublisher

**Путь:** `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/publisher/RateLimitEventPublisher.kt`

**Назначение:** Публикует события изменения rate limit политик

```kotlin
@Component
class RateLimitEventPublisher(
    @Autowired(required = false)
    private val redisTemplate: ReactiveStringRedisTemplate?
) {
    companion object {
        const val RATELIMIT_CACHE_CHANNEL = "ratelimit-cache-invalidation"
    }

    fun publishRateLimitChanged(rateLimitId: UUID): Mono<Long> {
        if (redisTemplate == null) {
            logger.warn("Redis недоступен — cache invalidation не опубликован: rateLimitId={}", rateLimitId)
            return Mono.just(0L)
        }

        return redisTemplate.convertAndSend(RATELIMIT_CACHE_CHANNEL, rateLimitId.toString())
            .doOnSuccess { subscribersCount ->
                logger.info("Rate limit cache invalidation опубликован: rateLimitId={}, subscribers={}",
                    rateLimitId, subscribersCount)
            }
            .doOnError { error ->
                logger.error("Ошибка публикации rate limit cache invalidation: rateLimitId={}, error={}",
                    rateLimitId, error.message)
            }
    }
}
```

### Где вызываются Publishers

**RouteEventPublisher:**

```kotlin
// ApprovalService.kt
fun approve(routeId: UUID): Mono<RouteResponse> {
    return routeRepository.updateStatus(routeId, RouteStatus.PUBLISHED)
        .flatMap { route ->
            routeEventPublisher.publishRouteChanged(routeId)  // ← Публикация
                .thenReturn(route)
        }
}

fun reject(routeId: UUID): Mono<RouteResponse> { ... }
fun rollback(routeId: UUID): Mono<RouteResponse> { ... }
```

**RateLimitEventPublisher:**

```kotlin
// RateLimitService.kt (gateway-admin)
fun create(request: CreateRateLimitRequest): Mono<RateLimitResponse> {
    return rateLimitRepository.save(rateLimit)
        .flatMap { saved ->
            rateLimitEventPublisher.publishRateLimitChanged(saved.id!!)  // ← Публикация
                .thenReturn(saved)
        }
}

fun update(id: UUID, request: UpdateRateLimitRequest): Mono<RateLimitResponse> { ... }
fun delete(id: UUID): Mono<Void> { ... }
```

---

## Компоненты gateway-core (Subscribers)

### RouteRefreshService

**Путь:** `backend/gateway-core/src/main/kotlin/com/company/gateway/core/route/RouteRefreshService.kt`

**Назначение:** Подписывается на Redis каналы и инициирует обновление кэша

```kotlin
@Service
class RouteRefreshService(
    private val redisConnectionFactory: ReactiveRedisConnectionFactory,
    private val cacheManager: RouteCacheManager
) {
    @Value("\${gateway.cache.invalidation-channel:route-cache-invalidation}")
    private lateinit var routeInvalidationChannel: String

    @Value("\${gateway.cache.ratelimit-invalidation-channel:ratelimit-cache-invalidation}")
    private lateinit var rateLimitInvalidationChannel: String

    @Value("\${gateway.cache.reconnect-delay-seconds:30}")
    private var reconnectDelaySeconds: Long = 30

    private val redisAvailable = AtomicBoolean(true)
    private val reconnecting = AtomicBoolean(false)

    @EventListener(ApplicationReadyEvent::class)
    fun subscribeToInvalidationEvents() {
        startRouteSubscription()
        startRateLimitSubscription()
    }
}
```

**Подписка на route-cache-invalidation:**

```kotlin
private fun startRouteSubscription() {
    // Cleanup предыдущей подписки для предотвращения memory leak
    routeSubscription.getAndSet(null)?.dispose()
    routeContainer.getAndSet(null)?.destroy()

    try {
        val newContainer = ReactiveRedisMessageListenerContainer(redisConnectionFactory)
        routeContainer.set(newContainer)

        val newSubscription = newContainer.receive(ChannelTopic.of(routeInvalidationChannel))
            .flatMap { message ->
                logger.info("Получено событие инвалидации маршрута: {}", message.message)
                cacheManager.refreshCache()  // ← Обновление кэша
            }
            .doOnSubscribe {
                logger.info("Подписка на Redis канал '{}' активирована", routeInvalidationChannel)
                redisAvailable.set(true)
                reconnecting.set(false)
            }
            .doOnError { error ->
                logger.warn("Ошибка подписки Redis: {}", error.message)
                redisAvailable.set(false)
                scheduleReconnect()
            }
            .onErrorResume { _ ->
                logger.warn("Redis недоступен, используем Caffeine cache с TTL fallback")
                Mono.empty()
            }
            .subscribe()

        routeSubscription.set(newSubscription)
    } catch (e: Exception) {
        logger.warn("Ошибка подключения к Redis: {}. Используем Caffeine fallback", e.message)
        redisAvailable.set(false)
        scheduleReconnect()
    }
}
```

**Важно:** AtomicReference используется для хранения контейнера и подписки, чтобы корректно cleanup при переподключении.

**Подписка на ratelimit-cache-invalidation:**

```kotlin
private fun startRateLimitSubscription() {
    container.receive(ChannelTopic.of(rateLimitInvalidationChannel))
        .flatMap { message ->
            val rateLimitId = UUID.fromString(message.message)
            logger.info("Получено событие инвалидации rate limit: {}", rateLimitId)
            cacheManager.refreshRateLimitCache(rateLimitId)  // ← Точечное обновление
        }
        .subscribe()
}
```

**Автоматическое переподключение:**

```kotlin
private fun scheduleReconnect() {
    if (!reconnecting.compareAndSet(false, true)) {
        return  // Уже идёт переподключение
    }

    Mono.delay(Duration.ofSeconds(reconnectDelaySeconds))
        .doOnNext { logger.info("Попытка переподключения к Redis...") }
        .doFinally { reconnecting.set(false) }
        .subscribe {
            startRouteSubscription()
            startRateLimitSubscription()
        }
}
```

### RouteCacheManager

**Путь:** `backend/gateway-core/src/main/kotlin/com/company/gateway/core/cache/RouteCacheManager.kt`

**Назначение:** Управляет кэшем маршрутов и rate limit политик

```kotlin
@Component
class RouteCacheManager(
    private val routeRepository: RouteRepository,
    private val rateLimitRepository: RateLimitRepository,
    private val caffeineRouteCache: Cache<String, List<Route>>,
    private val caffeineRateLimitCache: Cache<UUID, RateLimit>,
    private val eventPublisher: ApplicationEventPublisher
) {
    private val cachedRoutes = AtomicReference<List<Route>>(emptyList())
    private val cachedRateLimits = AtomicReference<Map<UUID, RateLimit>>(emptyMap())
}
```

**Полное обновление кэша (для маршрутов):**

```kotlin
fun refreshCache(): Mono<Void> =
    routeRepository.findByStatus(RouteStatus.PUBLISHED)
        .collectList()
        .flatMap { routes ->
            // Собираем уникальные rateLimitId
            val rateLimitIds = routes.mapNotNull { it.rateLimitId }.distinct()

            // Batch загрузка rate limits
            rateLimitRepository.findAllByIdIn(rateLimitIds)
                .collectList()
                .map { rateLimits ->
                    routes to rateLimits.associateBy { it.id!! }
                }
        }
        .doOnNext { (routes, rateLimits) ->
            // Обновляем AtomicReference и Caffeine
            cachedRoutes.set(routes)
            caffeineRouteCache.put(ROUTE_CACHE_KEY, routes)

            cachedRateLimits.set(rateLimits)
            rateLimits.forEach { (id, rateLimit) ->
                caffeineRateLimitCache.put(id, rateLimit)
            }

            logger.info("Кэш обновлён: {} маршрутов, {} rate limits", routes.size, rateLimits.size)

            // Уведомляем Spring Cloud Gateway
            eventPublisher.publishEvent(RefreshRoutesEvent(this))
        }
        .then()
```

**Точечное обновление rate limit:**

```kotlin
fun refreshRateLimitCache(rateLimitId: UUID): Mono<Void> =
    rateLimitRepository.findById(rateLimitId)
        .doOnNext { rateLimit ->
            // Обновляем AtomicReference map
            cachedRateLimits.updateAndGet { map ->
                map.toMutableMap().apply { put(rateLimitId, rateLimit) }
            }
            // Обновляем Caffeine
            caffeineRateLimitCache.put(rateLimitId, rateLimit)

            eventPublisher.publishEvent(RefreshRoutesEvent(this))
        }
        .switchIfEmpty(
            // Rate limit удалён — удаляем из кэша
            Mono.fromRunnable {
                cachedRateLimits.updateAndGet { map ->
                    map.toMutableMap().apply { remove(rateLimitId) }
                }
                caffeineRateLimitCache.invalidate(rateLimitId)
            }
        )
        .then()
```

**Синхронный fallback для DynamicRouteLocator:**

При первом запросе к маршруту, когда rate limit политика ещё не в кэше, используется синхронная загрузка:

```kotlin
/**
 * Синхронная загрузка политики — fallback для DynamicRouteLocator.
 * ВАЖНО: Блокирующий вызов, использовать только для fallback!
 */
fun loadRateLimitSync(rateLimitId: UUID): RateLimit? {
    return try {
        rateLimitRepository.findById(rateLimitId)
            .doOnNext { rateLimit ->
                // Кэшируем загруженную политику для следующих запросов
                cachedRateLimits.updateAndGet { map ->
                    map.toMutableMap().apply { put(rateLimitId, rateLimit) }
                }
                caffeineRateLimitCache.put(rateLimitId, rateLimit)
                logger.info("Политика rate limit загружена через fallback: {}", rateLimitId)
            }
            .block(Duration.ofSeconds(5))  // Блокирующий вызов с таймаутом
    } catch (e: Exception) {
        logger.error("Ошибка синхронной загрузки политики {}: {}", rateLimitId, e.message)
        null
    }
}
```

**Когда используется:** DynamicRouteLocator вызывает этот метод когда маршрут с rate limit публикуется, но кэш ещё не обновлён через Redis Pub/Sub.

---

## Fallback механизм

### Когда активируется Fallback?

Fallback активируется при недоступности Redis:
- Redis server не запущен
- Сетевые проблемы
- Timeout подключения

### Двухуровневый кэш

```
┌─────────────────────────────────────────────────────────────┐
│                    RouteCacheManager                         │
│                                                              │
│  ┌──────────────────────┐   ┌──────────────────────────────┐│
│  │   AtomicReference    │   │     Caffeine Cache           ││
│  │   ────────────────   │   │     ──────────────           ││
│  │   Primary (Redis OK) │   │     Fallback (TTL-based)     ││
│  │   Моментальные       │   │     Автоматическая           ││
│  │   обновления         │   │     инвалидация по TTL       ││
│  └──────────────────────┘   └──────────────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

**AtomicReference:**
- Обновляется при получении Redis события
- Используется как primary source
- Нет автоматической инвалидации

**Caffeine Cache:**
- TTL-based expiration (настраивается)
- Fallback при недоступности Redis
- Автоматическая перезагрузка при истечении TTL

### Логика получения данных

```kotlin
fun getCachedRoutes(): List<Route> {
    // 1. Пробуем AtomicReference (Redis-driven)
    val routes = cachedRoutes.get()
    if (routes.isNotEmpty()) {
        return routes
    }

    // 2. Fallback на Caffeine TTL кэш
    return caffeineRouteCache.getIfPresent(ROUTE_CACHE_KEY) ?: emptyList()
}
```

### Автоматическое восстановление

RouteRefreshService пытается переподключиться к Redis каждые 30 секунд:

```kotlin
private fun scheduleReconnect() {
    Mono.delay(Duration.ofSeconds(reconnectDelaySeconds))
        .subscribe {
            startRouteSubscription()      // Переподключение route канала
            startRateLimitSubscription()  // Переподключение ratelimit канала
        }
}
```

При успешном переподключении:
1. Логируется "Подписка активирована"
2. `redisAvailable.set(true)`
3. Следующие изменения доставляются в реальном времени

---

## Troubleshooting & Debugging

### Проверка состояния Redis

```bash
# Подключение к Redis контейнеру
docker exec -it apigateway-redis-1 redis-cli

# Проверка работоспособности
PING
# Ответ: PONG

# Информация о Redis
INFO server
```

### Просмотр Pub/Sub сообщений

```bash
# Подписаться на канал и видеть сообщения в реальном времени
docker exec -it apigateway-redis-1 redis-cli SUBSCRIBE route-cache-invalidation

# В другом терминале одобрите маршрут — увидите сообщение:
# 1) "message"
# 2) "route-cache-invalidation"
# 3) "550e8400-e29b-41d4-a716-446655440000"
```

### Проверка подписчиков

```bash
# Сколько клиентов подписано на канал
docker exec -it apigateway-redis-1 redis-cli PUBSUB NUMSUB route-cache-invalidation

# Ответ: route-cache-invalidation 2
# (означает 2 подписчика — например, 2 инстанса gateway-core)
```

### Ручная публикация тестового сообщения

```bash
# Опубликовать сообщение вручную
docker exec -it apigateway-redis-1 redis-cli PUBLISH route-cache-invalidation "test-uuid"

# Если gateway-core подписан, в логах появится:
# Получено событие инвалидации маршрута: test-uuid
```

### Логи для диагностики

**gateway-admin (Publisher):**
```
INFO  RouteEventPublisher - Cache invalidation опубликован: routeId=..., subscribers=2
WARN  RouteEventPublisher - Redis недоступен — cache invalidation не опубликован
```

**gateway-core (Subscriber):**
```
INFO  RouteRefreshService - Подписка на Redis канал 'route-cache-invalidation' активирована
INFO  RouteRefreshService - Получено событие инвалидации маршрута: 550e8400-...
INFO  RouteCacheManager   - Кэш обновлён: 15 маршрутов, 3 rate limit политик
WARN  RouteRefreshService - Ошибка подписки Redis, используем Caffeine TTL fallback
INFO  RouteRefreshService - Попытка переподключения к Redis...
```

### Частые проблемы и решения

#### Проблема: Маршрут одобрен, но не работает

**Симптомы:** HTTP 404 при запросе к новому маршруту

**Диагностика:**
1. Проверить логи gateway-admin — сообщение опубликовано?
2. Проверить логи gateway-core — сообщение получено?
3. Проверить `PUBSUB NUMSUB` — есть ли подписчики?

**Решения:**
- Если 0 подписчиков: перезапустить gateway-core
- Если сообщение не опубликовано: проверить Redis connectivity в gateway-admin
- Если сообщение получено, но маршрут не работает: проверить статус маршрута в БД

#### Проблема: Redis недоступен

**Симптомы:** WARN в логах "Redis недоступен"

**Решения:**
1. Проверить `docker ps` — Redis контейнер запущен?
2. Проверить `docker logs apigateway-redis-1` — нет ли ошибок?
3. Проверить сетевые настройки — правильный хост/порт в конфигурации?

#### Проблема: Задержка применения изменений

**Симптомы:** Маршрут работает через 30+ секунд после одобрения

**Причина:** Redis был недоступен, используется Caffeine TTL fallback

**Решения:**
1. Проверить подключение к Redis
2. После восстановления Redis изменения будут мгновенными

### Конфигурация

```yaml
# gateway-core/application.yml
gateway:
  cache:
    invalidation-channel: route-cache-invalidation
    ratelimit-invalidation-channel: ratelimit-cache-invalidation
    reconnect-delay-seconds: 30
    caffeine:
      route-cache-ttl-seconds: 60   # TTL для fallback кэша
      rate-limit-cache-ttl-seconds: 60

spring:
  data:
    redis:
      host: redis
      port: 6379
```

### Тестирование Pub/Sub локально

**С Testcontainers:**

```kotlin
@Testcontainers
class RedisPubSubTest {
    companion object {
        @Container
        @JvmStatic
        val redis = RedisContainer("redis:7")
    }

    @Test
    fun `публикация и подписка работают`() {
        // Подписаться
        val container = ReactiveRedisMessageListenerContainer(connectionFactory)
        val messages = mutableListOf<String>()

        container.receive(ChannelTopic.of("test-channel"))
            .doOnNext { messages.add(it.message) }
            .subscribe()

        // Подождать активации подписки (awaitility вместо Thread.sleep)
        await().atMost(500, TimeUnit.MILLISECONDS).until { true }

        // Опубликовать
        redisTemplate.convertAndSend("test-channel", "hello").block()

        // Проверить
        await().atMost(1, TimeUnit.SECONDS).until { messages.contains("hello") }
    }
}
```

---

## References

- [Redis Pub/Sub Documentation](https://redis.io/docs/latest/develop/interact/pubsub/)
- [Spring Data Redis — Pub/Sub](https://docs.spring.io/spring-data/redis/reference/redis/pubsub.html)
- [ReactiveRedisMessageListenerContainer](https://docs.spring.io/spring-data/redis/docs/current/api/org/springframework/data/redis/listener/ReactiveRedisMessageListenerContainer.html)
- [Caffeine Cache](https://github.com/ben-manes/caffeine)

---

*Последнее обновление: 2026-02-23*
