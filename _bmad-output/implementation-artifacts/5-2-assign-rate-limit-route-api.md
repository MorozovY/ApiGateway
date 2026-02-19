# Story 5.2: Assign Rate Limit to Route API

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **Developer**,
I want to assign a rate limit policy to my route,
so that my service is protected from excessive traffic (FR15).

## Acceptance Criteria

**AC1 — Назначение политики через PUT route:**

**Given** аутентифицированный developer с draft маршрутом
**When** PUT `/api/v1/routes/{id}` с body:
```json
{
  "rateLimitId": "policy-uuid-here"
}
```
**Then** маршрут обновляется с назначенной политикой rate limit
**And** response HTTP 200 с обновлённым маршрутом
**And** response включает полные детали rate limit:
```json
{
  "id": "route-uuid",
  "path": "/api/orders",
  "upstreamUrl": "http://order-service:8080",
  "methods": ["GET", "POST"],
  "status": "draft",
  "rateLimitId": "policy-uuid-here",
  "rateLimit": {
    "id": "policy-uuid",
    "name": "standard",
    "requestsPerSecond": 100,
    "burstSize": 150
  },
  ...
}
```

**AC2 — Валидация несуществующей политики:**

**Given** rateLimitId не существует в базе данных
**When** PUT `/api/v1/routes/{id}` с невалидным rateLimitId
**Then** response HTTP 400 Bad Request
**And** RFC 7807 body с detail: "Rate limit policy not found"

**AC3 — Удаление политики с маршрута:**

**Given** маршрут с назначенной политикой rate limit
**When** PUT `/api/v1/routes/{id}` с `"rateLimitId": null`
**Then** rate limit удаляется с маршрута
**And** маршрут сохраняется без rate limiting
**And** response HTTP 200 с обновлённым маршрутом (rateLimitId: null, rateLimit: null)

**AC4 — Cache invalidation при обновлении published маршрута с rate limit:**

**Given** published маршрут с назначенной политикой rate limit
**And** политика обновляется (изменяются лимиты)
**When** RateLimitService.update() выполняется
**Then** gateway-core получает cache invalidation event
**And** новые лимиты применяются в течение 5 секунд

**AC5 — Rate limit в GET route response:**

**Given** маршрут с назначенной политикой rate limit
**When** GET `/api/v1/routes/{id}`
**Then** response включает полный объект `rateLimit`:
```json
{
  "rateLimit": {
    "id": "...",
    "name": "standard",
    "requestsPerSecond": 100,
    "burstSize": 150
  }
}
```

**AC6 — Route без rate limit:**

**Given** маршрут без назначенной политики
**When** GET `/api/v1/routes/{id}`
**Then** response содержит `rateLimitId: null` и `rateLimit: null`

**AC7 — Rate limit в списке маршрутов:**

**Given** маршруты с и без rate limit политик
**When** GET `/api/v1/routes`
**Then** каждый маршрут в списке включает rateLimitId и rateLimit объект
**And** для маршрутов без политики rateLimit = null

## Tasks / Subtasks

- [x] Task 1: Обновить UpdateRouteRequest — добавить поле rateLimitId (AC1, AC3)
  - [x] Добавить `rateLimitId: UUID?` поле в UpdateRouteRequest
  - [x] rateLimitId опционально, nullable для удаления политики

- [x] Task 2: Обновить RouteResponse — добавить rateLimit объект (AC5, AC6, AC7)
  - [x] Добавить `rateLimitId: UUID?` поле
  - [x] Добавить `rateLimit: RateLimitInfo?` вложенный объект (не RateLimitResponse — без usageCount)
  - [x] Создать RateLimitInfo DTO (id, name, requestsPerSecond, burstSize)

- [x] Task 3: Обновить RouteService.update() — логика назначения rate limit (AC1, AC2, AC3)
  - [x] При rateLimitId != null — валидировать существование политики
  - [x] При rateLimitId = null — удалить политику с маршрута
  - [x] Сохранить маршрут с обновлённым rateLimitId

- [x] Task 4: Обновить RouteService.findById() — включить rateLimit данные (AC5, AC6)
  - [x] Если rateLimitId != null — загрузить политику из RateLimitRepository
  - [x] Включить данные политики в response

- [x] Task 5: Обновить RouteService.findAll() — включить rateLimit в список (AC7)
  - [x] Оптимизация: загрузить все политики за один запрос (избежать N+1)
  - [x] Заполнить rateLimit для каждого маршрута в списке

- [x] Task 6: Обновить RouteDetailResponse — включить rateLimit (AC5)
  - [x] Добавить `rateLimitId: UUID?` поле
  - [x] Добавить `rateLimit: RateLimitInfo?` вложенный объект
  - [x] Обновить RouteDetailResult DTO для JOIN с rate_limits

- [x] Task 7: Интеграционные тесты (AC1-AC7)
  - [x] Тест: назначение политики на маршрут — 200
  - [x] Тест: назначение несуществующей политики — 400
  - [x] Тест: удаление политики (rateLimitId: null) — 200
  - [x] Тест: GET route включает rateLimit объект
  - [x] Тест: GET routes список включает rateLimit для каждого маршрута

## Dev Notes

### Существующий код

**UpdateRouteRequest.kt** — текущие поля:
```kotlin
data class UpdateRouteRequest(
    val path: String? = null,
    val upstreamUrl: String? = null,
    val methods: List<String>? = null,
    val description: String? = null
)
```
**Добавить:** `val rateLimitId: UUID? = null`

**RouteResponse.kt** — текущие поля:
```kotlin
data class RouteResponse(
    val id: UUID,
    val path: String,
    val upstreamUrl: String,
    val methods: List<String>,
    val description: String?,
    val status: String,
    val createdBy: UUID?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val submittedAt: Instant? = null,
    val approvedBy: UUID? = null,
    val approvedAt: Instant? = null,
    val rejectedBy: UUID? = null,
    val rejectedAt: Instant? = null,
    val rejectionReason: String? = null
)
```
**Добавить:** `val rateLimitId: UUID? = null` и `val rateLimit: RateLimitInfo? = null`

**Route.kt** — уже содержит `rateLimitId: UUID?` (добавлено в Story 5.1, Task 8)

### RateLimitInfo DTO

Создать минималистичный DTO без usageCount (для встраивания в RouteResponse):

```kotlin
// Путь: backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/RateLimitInfo.kt

package com.company.gateway.admin.dto

import com.company.gateway.common.model.RateLimit
import java.util.UUID

/**
 * Краткая информация о политике rate limiting для встраивания в RouteResponse.
 *
 * В отличие от RateLimitResponse, не содержит usageCount и audit поля,
 * так как используется как вложенный объект.
 */
data class RateLimitInfo(
    val id: UUID,
    val name: String,
    val requestsPerSecond: Int,
    val burstSize: Int
) {
    companion object {
        fun from(rateLimit: RateLimit): RateLimitInfo {
            return RateLimitInfo(
                id = rateLimit.id!!,
                name = rateLimit.name,
                requestsPerSecond = rateLimit.requestsPerSecond,
                burstSize = rateLimit.burstSize
            )
        }
    }
}
```

### RouteService.update() — паттерн валидации rateLimitId

```kotlin
// В RouteService.update() после проверки ownership и статуса
// Добавить валидацию rateLimitId

val rateLimitCheck = if (request.rateLimitId != null) {
    rateLimitRepository.existsById(request.rateLimitId)
        .flatMap { exists ->
            if (!exists) {
                Mono.error<Boolean>(ValidationException("Rate limit policy not found"))
            } else {
                Mono.just(true)
            }
        }
} else {
    Mono.just(true) // null означает удаление политики — валидно
}

// Затем в копии route:
val updatedRoute = route.copy(
    path = request.path ?: route.path,
    upstreamUrl = request.upstreamUrl ?: route.upstreamUrl,
    methods = request.methods ?: route.methods,
    description = request.description ?: route.description,
    rateLimitId = request.rateLimitId, // null удаляет, UUID назначает
    updatedAt = Instant.now()
)
```

### N+1 оптимизация для findAll()

Для избежания N+1 при загрузке rateLimit для списка маршрутов:

```kotlin
// В RouteService.findAllWithFilters()

// 1. Загрузить маршруты
val routes = routeRepository.findWithFilters(...).collectList()

// 2. Собрать уникальные rateLimitId (исключая null)
val rateLimitIds = routes.mapNotNull { it.rateLimitId }.distinct()

// 3. Загрузить все политики за один запрос
val rateLimitsMap = rateLimitRepository.findAllById(rateLimitIds)
    .collectMap({ it.id!! }, { RateLimitInfo.from(it) })

// 4. Собрать response с rateLimit
routes.map { route ->
    RouteResponse.from(route, rateLimitsMap[route.rateLimitId])
}
```

### Изменения в RouteResponse.from()

```kotlin
companion object {
    /**
     * Создаёт RouteResponse из Route entity.
     *
     * @param route маршрут
     * @param rateLimit информация о политике rate limit (опционально)
     */
    fun from(route: Route, rateLimit: RateLimitInfo? = null): RouteResponse {
        return RouteResponse(
            id = route.id!!,
            path = route.path,
            upstreamUrl = route.upstreamUrl,
            methods = route.methods,
            description = route.description,
            status = route.status.name.lowercase(),
            createdBy = route.createdBy,
            createdAt = route.createdAt,
            updatedAt = route.updatedAt,
            submittedAt = route.submittedAt,
            approvedBy = route.approvedBy,
            approvedAt = route.approvedAt,
            rejectedBy = route.rejectedBy,
            rejectedAt = route.rejectedAt,
            rejectionReason = route.rejectionReason,
            rateLimitId = route.rateLimitId,
            rateLimit = rateLimit
        )
    }
}
```

### RouteDetailResponse — обновление

RouteDetailResponse используется в findByIdWithCreator(). Нужно:
1. Добавить JOIN с rate_limits в SQL запросе
2. Добавить rateLimitId и rateLimit поля в RouteDetailResult и RouteDetailResponse

```kotlin
// В RouteRepositoryCustomImpl.findByIdWithCreator() — обновить SQL:
"""
SELECT r.*, u.username as creator_username, u.email as creator_email,
       rl.id as rl_id, rl.name as rl_name, rl.requests_per_second, rl.burst_size
FROM routes r
LEFT JOIN users u ON r.created_by = u.id
LEFT JOIN rate_limits rl ON r.rate_limit_id = rl.id
WHERE r.id = :id
"""

// RouteDetailResult — добавить поля:
data class RouteDetailResult(
    // ... существующие поля ...
    val rateLimitId: UUID? = null,
    val rlId: UUID? = null,
    val rlName: String? = null,
    val requestsPerSecond: Int? = null,
    val burstSize: Int? = null
) {
    fun toResponse(): RouteDetailResponse {
        val rateLimitInfo = if (rlId != null) {
            RateLimitInfo(
                id = rlId,
                name = rlName!!,
                requestsPerSecond = requestsPerSecond!!,
                burstSize = burstSize!!
            )
        } else null

        return RouteDetailResponse(
            // ... существующие поля ...
            rateLimitId = rateLimitId,
            rateLimit = rateLimitInfo
        )
    }
}
```

### Важные паттерны из Story 5.1

1. **ValidationException vs BadRequest:** используй ValidationException для невалидного rateLimitId — GlobalExceptionHandler вернёт 400

2. **Audit logging:** обновление rate limit на маршруте логируется как `route.updated` (уже реализовано в RouteService.update())

3. **Cache invalidation:** уже реализовано в RateLimitService.update() — при изменении политики публикуется event для всех маршрутов с этой политикой

4. **Reactive patterns:** всегда использовать Mono/Flux chains, без .block()

### Структура файлов для изменения

| Файл | Действие |
|------|---------|
| `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/UpdateRouteRequest.kt` | ИЗМЕНИТЬ — добавить rateLimitId |
| `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/RateLimitInfo.kt` | СОЗДАТЬ — новый DTO |
| `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/RouteResponse.kt` | ИЗМЕНИТЬ — добавить rateLimitId, rateLimit |
| `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/RouteDetailResponse.kt` | ИЗМЕНИТЬ — добавить rateLimitId, rateLimit |
| `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/RouteService.kt` | ИЗМЕНИТЬ — валидация rateLimitId, загрузка rateLimit |
| `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/RouteRepositoryCustomImpl.kt` | ИЗМЕНИТЬ — JOIN с rate_limits |
| `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/RouteRateLimitIntegrationTest.kt` | СОЗДАТЬ — интеграционные тесты |

### Project Structure Notes

```
backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/
├── dto/
│   ├── RateLimitInfo.kt          # СОЗДАТЬ — минимальный DTO для встраивания
│   ├── RouteResponse.kt          # ИЗМЕНИТЬ — +rateLimitId, +rateLimit
│   ├── RouteDetailResponse.kt    # ИЗМЕНИТЬ — +rateLimitId, +rateLimit
│   └── UpdateRouteRequest.kt     # ИЗМЕНИТЬ — +rateLimitId
├── repository/
│   └── RouteRepositoryCustomImpl.kt  # ИЗМЕНИТЬ — JOIN с rate_limits
└── service/
    └── RouteService.kt           # ИЗМЕНИТЬ — валидация и загрузка rateLimit

backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/
└── RouteRateLimitIntegrationTest.kt  # СОЗДАТЬ
```

### Архитектурные требования

- **Reactive patterns**: Mono/Flux chains, без .block()
- **RFC 7807**: все ошибки через GlobalExceptionHandler (ValidationException → 400)
- **snake_case**: колонки БД (`rate_limit_id`)
- **camelCase**: JSON поля (`rateLimitId`, `rateLimit`)
- **N+1 prevention**: batch загрузка rate limits в списке маршрутов
- **Комментарии**: только на русском языке
- **Названия тестов**: только на русском языке
- **Testcontainers**: для интеграционных тестов

### Тесты на русском языке (примеры)

```kotlin
@Test
fun `назначение rate limit политики возвращает 200`() { ... }

@Test
fun `назначение несуществующей политики возвращает 400`() { ... }

@Test
fun `удаление политики через rateLimitId null возвращает 200`() { ... }

@Test
fun `GET route включает rateLimit объект`() { ... }

@Test
fun `GET routes список включает rateLimit для каждого маршрута`() { ... }
```

### References

- [Source: planning-artifacts/epics.md#Story-5.2] — Story requirements и AC
- [Source: planning-artifacts/architecture.md#Data-Architecture] — PostgreSQL, R2DBC, snake_case
- [Source: planning-artifacts/architecture.md#API-Format] — REST, RFC 7807, camelCase JSON
- [Source: implementation-artifacts/5-1-rate-limit-policy-crud-api.md] — предыдущая story, паттерны
- [Source: backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/RouteService.kt] — паттерн сервиса
- [Source: backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/RateLimitService.kt] — паттерн работы с rate limits
- [Source: backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/RateLimitResponse.kt] — паттерн DTO
- [Source: backend/gateway-common/src/main/kotlin/com/company/gateway/common/model/Route.kt] — entity с rateLimitId

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

N/A — реализация прошла без блокирующих проблем.

### Completion Notes List

1. **Task 1 (UpdateRouteRequest):** Добавлено поле `rateLimitId: UUID?` для назначения/удаления политики rate limit через PUT /api/v1/routes/{id}.

2. **Task 2 (RouteResponse + RateLimitInfo):** Создан новый DTO `RateLimitInfo` (минимальный, без usageCount). RouteResponse расширен полями `rateLimitId` и `rateLimit`. Обновлён метод `from()` с опциональным параметром rateLimit.

3. **Task 3 (RouteService.update):** Добавлена валидация rateLimitId через RateLimitRepository.existsById(). ValidationException для несуществующей политики (→ HTTP 400). Поддержка null для удаления политики с маршрута.

4. **Task 4 (RouteService.findById):** Метод теперь загружает RateLimitInfo если у маршрута назначена политика.

5. **Task 5 (RouteService.findAll — N+1):** Реализована batch-загрузка rate limits через `findAllById()`. Уникальные rateLimitId собираются из маршрутов, затем все политики загружаются за один запрос. Map используется для сопоставления политик с маршрутами.

6. **Task 6 (RouteDetailResponse + SQL JOIN):** RouteDetailResponse и RouteWithCreator расширены полями rateLimit. SQL запросы в RouteRepositoryCustomImpl обновлены для JOIN с таблицей rate_limits. Маппинг строк обновлён для извлечения rl_name, rl_requests_per_second, rl_burst_size.

7. **Task 7 (Интеграционные тесты):** Создан RouteRateLimitIntegrationTest.kt с 10 тестами, покрывающими AC1-AC7. Все тесты проходят успешно.

### Change Log

- 2026-02-19: Story 5.2 — Assign Rate Limit to Route API — реализация завершена

### File List

**Новые файлы:**
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/RateLimitInfo.kt
- backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/RouteRateLimitIntegrationTest.kt

**Изменённые файлы:**
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/UpdateRouteRequest.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/RouteResponse.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/RouteDetailResponse.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/RouteService.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/RouteRepositoryCustomImpl.kt
- backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/service/RouteServiceTest.kt
