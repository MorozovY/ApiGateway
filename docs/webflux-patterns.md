# Spring WebFlux — Reactive паттерны

Документация описывает reactive паттерны, используемые в проекте API Gateway. Предназначена для новых разработчиков, чтобы избежать типичных ошибок при работе с non-blocking кодом.

## Содержание

- [Введение](#введение)
- [Mono vs Flux — когда использовать](#mono-vs-flux--когда-использовать)
- [Основные операторы](#основные-операторы)
- [Обработка ошибок](#обработка-ошибок)
- [Комбинирование потоков](#комбинирование-потоков)
- [Side Effects](#side-effects)
- [Тестирование](#тестирование)
- [Anti-patterns и типичные ошибки](#anti-patterns-и-типичные-ошибки)
- [Примеры из проекта](#примеры-из-проекта)

---

## Введение

### Зачем нужен reactive stack?

API Gateway построен на **Spring WebFlux** — reactive web framework. Это означает:

1. **Non-blocking I/O** — потоки не блокируются при ожидании ответа от базы данных, Redis или upstream сервисов
2. **Event-driven** — обработка запросов основана на событиях, а не на ожидании
3. **Backpressure** — система может контролировать скорость обработки данных

**Преимущества:**
- Высокая пропускная способность при ограниченном количестве потоков
- Эффективное использование ресурсов (1 поток Netty может обслуживать тысячи соединений)
- Масштабируемость без увеличения thread pool

**Когда не использовать reactive:**
- CPU-bound задачи (сложные вычисления) — reactive не даёт преимуществ
- Простые CRUD приложения без высокой нагрузки — overhead от reactive может не оправдаться

### Cold vs Hot Publishers

**Cold Publisher** — создаёт новый поток данных для каждого подписчика:

```kotlin
// Каждый subscriber получает свежие данные из БД
val route = routeRepository.findById(id)

route.subscribe()  // Первый запрос к БД
route.subscribe()  // Второй запрос к БД
```

**Hot Publisher** — все подписчики получают одни и те же данные:

```kotlin
// Redis Pub/Sub — все подписчики получают одно сообщение
container.receive(ChannelTopic.of("route-cache-invalidation"))
    .subscribe()  // Подписчик A
    .subscribe()  // Подписчик B — получит те же события
```

В проекте большинство операций — **Cold**: запросы к БД, Redis, upstream сервисы. **Hot** используется для Redis Pub/Sub подписок.

---

## Mono vs Flux — когда использовать

### Mono<T> — 0 или 1 элемент

Используется когда ожидается **единственный результат** или его отсутствие:

```kotlin
// Получение одного маршрута по ID
fun findById(id: UUID): Mono<Route>

// Проверка существования (true/false)
fun existsByPath(path: String): Mono<Boolean>

// Создание сущности
fun save(route: Route): Mono<Route>

// Void операции (delete, update без возврата)
fun delete(route: Route): Mono<Void>
```

### Flux<T> — 0..N элементов

Используется когда ожидается **коллекция** результатов:

```kotlin
// Список маршрутов
fun findAll(): Flux<Route>

// Поиск с фильтрацией
fun findByStatus(status: RouteStatus): Flux<Route>

// Поток событий (Redis Pub/Sub)
fun receive(topic: ChannelTopic): Flux<Message>
```

### Преобразование между Mono и Flux

```kotlin
// Flux → Mono (собрать в список)
routeRepository.findAll()
    .collectList()  // Mono<List<Route>>

// Flux → Mono (первый элемент)
routeRepository.findByStatus(DRAFT)
    .next()  // Mono<Route>

// Flux → Mono (количество)
routeRepository.findAll()
    .count()  // Mono<Long>

// Mono → Flux (один элемент как поток)
Mono.just("hello")
    .flux()  // Flux<String>
```

---

## Основные операторы

### map — синхронная трансформация

Используется для **синхронного** преобразования значения:

```kotlin
// Преобразование entity → DTO
routeRepository.findById(id)
    .map { route -> RouteResponse.from(route) }

// Извлечение поля
userRepository.findById(userId)
    .map { it.username }  // Mono<String>
```

**Важно:** В `map` нельзя вызывать асинхронные операции — используйте `flatMap`.

### flatMap — асинхронная трансформация

Используется когда трансформация **возвращает Mono/Flux**:

```kotlin
// Загрузка связанных данных
routeRepository.findById(id)
    .flatMap { route ->
        // rateLimitRepository.findById возвращает Mono
        rateLimitRepository.findById(route.rateLimitId)
            .map { rateLimit -> RouteResponse.from(route, rateLimit) }
    }
```

**Пример из проекта** (`RouteService.kt:60-124`):

```kotlin
fun create(request: CreateRouteRequest, userId: UUID): Mono<RouteResponse> {
    return routeRepository.existsByPath(request.path)
        .flatMap { exists ->
            if (exists) {
                Mono.error(ConflictException("Route with path already exists"))
            } else {
                routeRepository.save(route)
            }
        }
        .flatMap { savedRoute ->
            loadRateLimitInfo(savedRoute.rateLimitId)
                .map { info -> RouteResponse.from(savedRoute, info) }
        }
}
```

### filter — фильтрация элементов

```kotlin
// Flux — оставить только published маршруты
routeRepository.findAll()
    .filter { it.status == RouteStatus.PUBLISHED }

// Mono — пропустить, если условие не выполнено
routeRepository.findById(id)
    .filter { it.status == RouteStatus.DRAFT }
    // Если status != DRAFT, Mono станет empty
```

### switchIfEmpty — альтернатива при пустом Mono

Используется когда нужен **fallback Mono** если исходный пустой:

```kotlin
// Загрузить rate limit или вернуть route без него
loadRateLimitInfo(savedRoute.rateLimitId)
    .map { rateLimitInfo -> RouteResponse.from(savedRoute, rateLimitInfo, username) }
    .switchIfEmpty(
        Mono.defer { Mono.just(RouteResponse.from(savedRoute, null, username)) }
    )
```

**Важно:** `Mono.defer {}` откладывает создание Mono до момента подписки. Без defer Mono создастся сразу, даже если не понадобится.

### defaultIfEmpty — default значение при пустом Mono

Используется когда нужно **простое значение** (не Mono):

```kotlin
// Вернуть пустой список если маршрутов нет
routeRepository.findByStatus(DRAFT)
    .collectList()
    .defaultIfEmpty(emptyList())

// Вернуть route с null username если пользователь не найден
loadCreatorUsername(route.createdBy)
    .map { creatorUsername -> RouteResponse.from(route, null, creatorUsername) }
    .defaultIfEmpty(RouteResponse.from(route, null, null))
```

---

## Обработка ошибок

### onErrorResume — fallback Mono при ошибке

Используется когда нужен **альтернативный результат** при ошибке:

```kotlin
// Redis fallback при ошибке подписки
container.receive(ChannelTopic.of(routeInvalidationChannel))
    .flatMap { message ->
        cacheManager.refreshCache()
    }
    .onErrorResume { error ->
        logger.warn("Redis недоступен: {}", error.message)
        Mono.empty()  // Продолжаем без Redis
    }
```

**Пример из проекта** (`RouteRefreshService.kt:89-92`):

```kotlin
.onErrorResume { _ ->
    logger.warn("Redis недоступен, используем Caffeine cache с TTL fallback")
    Mono.empty()
}
```

**Пример из проекта** (`RateLimitService.kt:65-67`) — fallback при недоступности Redis:

```kotlin
tokenBucketScript.checkRateLimit(bucketKey, rateLimit.requestsPerSecond, rateLimit.burstSize)
    .doOnNext { result ->
        if (usingFallback.compareAndSet(true, false)) {
            logger.info("Redis recovered, rate limiting returned to distributed mode")
        }
    }
    .onErrorResume { ex ->
        handleRedisError(ex, bucketKey, rateLimit)  // Переключается на local fallback
    }
```

### onErrorReturn — fallback значение при ошибке

Используется для **простого значения** (не Mono):

```kotlin
// Вернуть false при ошибке проверки
routeRepository.existsByPath(path)
    .onErrorReturn(false)

// Вернуть пустой список при ошибке
routeRepository.findAll()
    .collectList()
    .onErrorReturn(emptyList())
```

### onErrorMap — трансформация исключений

Используется для **преобразования типа ошибки**:

```kotlin
// Конвертация DataIntegrityViolationException → ConflictException
routeRepository.save(route)
    .onErrorMap(DataIntegrityViolationException::class.java) { ex ->
        ConflictException("Route with path already exists")
    }
```

### retryWhen — повторные попытки при ошибках

Используется для **автоматических retry** при transient errors:

```kotlin
// Retry при конфликте path (race condition)
routeRepository.save(cloned)
    .retryWhen(
        Retry.max(3)
            .filter { it is DataIntegrityViolationException }
            .doBeforeRetry {
                logger.debug("Retry из-за конфликта, попытка {}", it.totalRetries() + 1)
            }
    )
```

**Пример из проекта** (`RouteService.kt:451-455`) — клонирование маршрута с автоматическим retry при конфликте уникального path.

**Когда использовать:**
- Network timeouts (transient errors)
- Database lock conflicts
- Race conditions при параллельных операциях

### Выбор паттерна обработки ошибок

| Ситуация | Паттерн |
|----------|---------|
| Нужен fallback Mono (пустой или с данными) | `onErrorResume` |
| Нужно простое значение | `onErrorReturn` |
| Нужно преобразовать тип ошибки | `onErrorMap` |
| Нужно залогировать и пробросить ошибку | `doOnError` + не ловить |
| Нужно повторить операцию при transient error | `retryWhen` |

### RFC 7807 Error Response

Все ошибки API должны возвращаться в формате **RFC 7807 Problem Details**:

```json
{
    "type": "https://api.gateway/errors/not-found",
    "title": "Not Found",
    "status": 404,
    "detail": "Route not found",
    "correlationId": "abc-123-def"
}
```

В проекте используется `GlobalExceptionHandler` для автоматической конвертации исключений в RFC 7807 формат.

---

## Комбинирование потоков

### flatMap — последовательное выполнение

Каждый элемент обрабатывается независимо, но **параллельность ограничена**:

```kotlin
// Загрузить маршрут, затем его rate limit
routeRepository.findById(id)
    .flatMap { route ->
        rateLimitRepository.findById(route.rateLimitId)
            .map { rateLimit -> route to rateLimit }
    }
```

### zip / zipWith — объединение нескольких Mono

Используется когда нужно **дождаться всех результатов**:

```kotlin
// Параллельная загрузка двух независимых запросов
val routesMono = routeRepository.findWithFilters(...)
val totalMono = routeRepository.countWithFilters(...)

Mono.zip(routesMono, totalMono)
    .map { tuple ->
        PagedResponse(
            items = tuple.t1,
            total = tuple.t2,
            offset = filter.offset,
            limit = filter.limit
        )
    }
```

**Пример из проекта** (`RouteService.kt:661-707`):

```kotlin
// Batch-загрузка пользователей и rate limits
val usersMono = userRepository.findAllById(userIds).collectMap({ it.id!! }, { it.username })
val rateLimitsMono = rateLimitRepository.findAllById(rateLimitIds).collectMap({ it.id!! }, { RateLimitInfo.from(it) })

Mono.zip(usersMono, rateLimitsMono)
    .map { lookups ->
        val usersMap = lookups.t1
        val rateLimitsMap = lookups.t2
        // Используем оба map для построения response
    }
```

### merge — объединение Flux без гарантии порядка

```kotlin
// События из нескольких каналов (порядок не важен)
Flux.merge(
    container.receive(ChannelTopic.of("channel1")),
    container.receive(ChannelTopic.of("channel2"))
)
```

### concat — последовательное объединение Flux

```kotlin
// Сначала все элементы первого Flux, потом второго
Flux.concat(
    routeRepository.findByStatus(PUBLISHED),
    routeRepository.findByStatus(DRAFT)
)
```

---

## Side Effects

Side effects — операции, которые не влияют на поток данных, но выполняют побочные действия (логирование, метрики, уведомления).

### doOnNext — при каждом элементе

```kotlin
routeRepository.findAll()
    .doOnNext { route ->
        logger.debug("Обработка маршрута: {}", route.path)
    }
```

### doOnError — при ошибке

```kotlin
rateLimitRepository.findById(id)
    .doOnError { error ->
        logger.error("Ошибка загрузки rate limit: {}", error.message)
        metrics.increment("ratelimit_load_errors")
    }
```

### doOnSuccess — при успешном завершении Mono

```kotlin
routeRepository.save(route)
    .doOnSuccess {
        logger.info("Маршрут создан: path={}", route.path)
    }
```

### doOnSubscribe — при подписке

```kotlin
container.receive(ChannelTopic.of(routeInvalidationChannel))
    .doOnSubscribe {
        logger.info("Подписка на Redis канал '{}' активирована", routeInvalidationChannel)
    }
```

**Пример из проекта** (`RouteRefreshService.kt:79-83`):

```kotlin
.doOnSubscribe {
    logger.info("Подписка на Redis канал '{}' активирована", routeInvalidationChannel)
    redisAvailable.set(true)
    reconnecting.set(false)
}
```

---

## Тестирование

### StepVerifier — unit тесты reactive кода

**StepVerifier** — основной инструмент для тестирования `Mono` и `Flux`:

```kotlin
@Test
fun `первый запрос разрешён и инициализирует bucket с полным burst`() {
    val key = "ratelimit:${UUID.randomUUID()}:client1"

    StepVerifier.create(tokenBucketScript.checkRateLimit(key, 10, 15))
        .assertNext { result ->
            assertThat(result.allowed).isTrue()
            assertThat(result.remaining).isEqualTo(14)  // 15 - 1
        }
        .verifyComplete()
}
```

**Проверка ошибок:**

```kotlin
@Test
fun `выбрасывает NotFoundException для несуществующего маршрута`() {
    StepVerifier.create(routeService.findById(UUID.randomUUID()))
        .expectError(NotFoundException::class.java)
        .verify()
}
```

**Проверка пустого Mono:**

```kotlin
@Test
fun `возвращает empty для несуществующего rate limit`() {
    StepVerifier.create(rateLimitRepository.findById(UUID.randomUUID()))
        .verifyComplete()  // Mono.empty()
}
```

### WebTestClient — integration тесты API

```kotlin
@Test
fun `создание маршрута возвращает 201 Created`() {
    webTestClient.post()
        .uri("/api/v1/routes")
        .bodyValue(CreateRouteRequest(
            path = "/api/orders",
            upstreamUrl = "http://order-service:8080",
            methods = listOf("GET", "POST")
        ))
        .exchange()
        .expectStatus().isCreated
        .expectBody<RouteResponse>()
        .value { response ->
            assertThat(response.path).isEqualTo("/api/orders")
            assertThat(response.status).isEqualTo("draft")
        }
}
```

**Проверка ошибок (RFC 7807):**

```kotlin
@Test
fun `создание дублирующего маршрута возвращает 409 Conflict`() {
    webTestClient.post()
        .uri("/api/v1/routes")
        .bodyValue(CreateRouteRequest(path = "/api/existing", ...))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        .expectBody()
        .jsonPath("$.type").isEqualTo("https://api.gateway/errors/conflict")
        .jsonPath("$.title").isEqualTo("Conflict")
        .jsonPath("$.status").isEqualTo(409)
        .jsonPath("$.correlationId").exists()
}

@Test
fun `запрос несуществующего маршрута возвращает 404 Not Found`() {
    webTestClient.get()
        .uri("/api/v1/routes/{id}", UUID.randomUUID())
        .exchange()
        .expectStatus().isNotFound
        .expectBody()
        .jsonPath("$.type").isEqualTo("https://api.gateway/errors/not-found")
}
```

### Awaitility — async assertions

**ВАЖНО:** Используйте `Awaitility` вместо `Thread.sleep()` для ожидания асинхронных событий:

```kotlin
// Правильно — Awaitility
await().atMost(5.seconds).untilAsserted {
    assertThat(cacheManager.getRoutes()).isNotEmpty()
}

// Неправильно — Thread.sleep
Thread.sleep(5000)  // Блокирует поток!
assertThat(cacheManager.getRoutes()).isNotEmpty()
```

### Testcontainers — внешние зависимости

Для integration тестов с Redis, PostgreSQL используйте **Testcontainers**:

```kotlin
@Testcontainers
class TokenBucketScriptTest {

    companion object {
        @Container
        @JvmStatic
        val redis = RedisContainer("redis:7")
    }

    @BeforeEach
    fun setUp() {
        connectionFactory = LettuceConnectionFactory(redis.host, redis.firstMappedPort)
        connectionFactory.afterPropertiesSet()
        // ...
    }

    @AfterEach
    fun tearDown() {
        // Очистка данных между тестами
        redisTemplate.keys("ratelimit:*")
            .flatMap { key -> redisTemplate.delete(key) }
            .blockLast()
        connectionFactory.destroy()
    }
}
```

---

## Anti-patterns и типичные ошибки

### ❌ Блокирующие вызовы (.block(), Thread.sleep())

**Проблема:** Блокируют поток Netty, уничтожая преимущества reactive stack.

```kotlin
// ❌ НЕПРАВИЛЬНО
fun getRoute(id: UUID): Route {
    return routeRepository.findById(id).block()!!  // Блокирует поток!
}

// ✅ ПРАВИЛЬНО
fun getRoute(id: UUID): Mono<Route> {
    return routeRepository.findById(id)
}
```

**Исключение:** `.block()` допустим **только** в тестах или в fallback-коде с явным таймаутом:

```kotlin
// ОК в fallback с таймаутом
fun loadRateLimitSync(rateLimitId: UUID): RateLimit? {
    return try {
        rateLimitRepository.findById(rateLimitId)
            .block(Duration.ofSeconds(5))  // Явный таймаут!
    } catch (e: Exception) {
        null
    }
}
```

### ❌ @PostConstruct в reactive сервисах

**Проблема:** `@PostConstruct` выполняется до полной инициализации Spring контекста, reactive beans могут быть не готовы.

```kotlin
// ❌ НЕПРАВИЛЬНО — в @Service, который использует reactive beans
@PostConstruct
fun init() {
    startRedisSubscription()  // Может упасть!
}

// ✅ ПРАВИЛЬНО — используйте @EventListener
@EventListener(ApplicationReadyEvent::class)
fun subscribeToInvalidationEvents() {
    startRouteSubscription()
    startRateLimitSubscription()
}
```

**Исключение:** `@PostConstruct` допустим в `@Configuration` классах для ранней инициализации (до reactive контекста), например для настройки MDC context propagation (`MdcContextConfig.kt`).

**Пример из проекта** (`RouteRefreshService.kt:54-58`):

```kotlin
@EventListener(ApplicationReadyEvent::class)
fun subscribeToInvalidationEvents() {
    startRouteSubscription()
    startRateLimitSubscription()
}
```

### ❌ ThreadLocal без context propagation

**Проблема:** ThreadLocal работает на основе потока. В reactive модели один запрос может обрабатываться несколькими потоками.

```kotlin
// ❌ НЕПРАВИЛЬНО — MDC потеряется при переключении потоков
MDC.put("correlationId", correlationId)
routeRepository.findById(id)
    .map { route ->
        logger.info("Found route")  // correlationId может быть null!
    }

// ✅ ПРАВИЛЬНО — использовать Reactor Context
Mono.deferContextual { ctx ->
    val correlationId = ctx.getOrDefault("correlationId", "unknown")
    MDC.put("correlationId", correlationId)
    routeRepository.findById(id)
}
```

В проекте MDC Context Propagation настроен в `MdcContextConfig.kt`.

### ❌ synchronized блоки

**Проблема:** `synchronized` блокирует поток, что противоречит non-blocking модели.

```kotlin
// ❌ НЕПРАВИЛЬНО
private var redisAvailable = true
private val lock = Any()

fun checkRedis() {
    synchronized(lock) {
        redisAvailable = false  // Блокирует поток!
    }
}

// ✅ ПРАВИЛЬНО — используйте AtomicReference
private val redisAvailable = AtomicBoolean(true)

fun checkRedis() {
    redisAvailable.set(false)  // Атомарно, без блокировки
}
```

**Пример из проекта** (`RouteRefreshService.kt:46-52`):

```kotlin
private val routeSubscription = AtomicReference<Disposable?>(null)
private val rateLimitSubscription = AtomicReference<Disposable?>(null)
private val routeContainer = AtomicReference<ReactiveRedisMessageListenerContainer?>(null)
private val rateLimitContainer = AtomicReference<ReactiveRedisMessageListenerContainer?>(null)
private val redisAvailable = AtomicBoolean(true)
private val reconnecting = AtomicBoolean(false)
```

### ❌ Игнорирование Mono/Flux (не подписались)

**Проблема:** Без `.subscribe()` или терминального оператора ничего не произойдёт.

```kotlin
// ❌ НЕПРАВИЛЬНО — событие не отправится
routeEventPublisher.publishRouteChanged(routeId)  // Возвращает Mono, но никто не подписался!

// ✅ ПРАВИЛЬНО — включить в цепочку
routeRepository.save(route)
    .flatMap { savedRoute ->
        routeEventPublisher.publishRouteChanged(savedRoute.id!!)
            .thenReturn(savedRoute)
    }
```

---

## Примеры из проекта

### RouteRefreshService — reactive subscription

**Файл:** `backend/gateway-core/src/main/kotlin/com/company/gateway/core/route/RouteRefreshService.kt`

Демонстрирует:
- `@EventListener(ApplicationReadyEvent::class)` вместо `@PostConstruct`
- `AtomicReference` и `AtomicBoolean` для thread-safe state
- `onErrorResume` для fallback при ошибке Redis
- `doOnSubscribe`, `doOnError` для side effects

### RouteService — flatMap chains и switchIfEmpty

**Файл:** `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/RouteService.kt`

Демонстрирует:
- Цепочки `flatMap` для последовательных async операций
- `switchIfEmpty` для альтернативного значения
- `Mono.zip` для параллельной загрузки данных
- `retryWhen` для обработки race conditions (строки 451-455)

### RateLimitService — onErrorResume fallback

**Файл:** `backend/gateway-core/src/main/kotlin/com/company/gateway/core/ratelimit/RateLimitService.kt`

Демонстрирует:
- `onErrorResume` для fallback при недоступности Redis
- `AtomicBoolean` для thread-safe флага состояния
- Graceful degradation (переключение на local mode)

### TokenBucketScriptTest — тестирование с StepVerifier

**Файл:** `backend/gateway-core/src/test/kotlin/com/company/gateway/core/ratelimit/TokenBucketScriptTest.kt`

Демонстрирует:
- `StepVerifier.create()` для тестирования Mono/Flux
- `.assertNext {}` для проверки элементов
- `.verifyComplete()` для проверки завершения
- Testcontainers для Redis

---

## References

- [Project Reactor Documentation](https://projectreactor.io/docs/core/release/reference/)
- [Spring WebFlux Reference](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [StepVerifier Documentation](https://projectreactor.io/docs/test/release/api/reactor/test/StepVerifier.html)
- [RFC 7807 Problem Details](https://tools.ietf.org/html/rfc7807)
- [CLAUDE.md](../CLAUDE.md) — правила проекта по reactive паттернам

---

*Последнее обновление: 2026-02-23*
