# Story 4.2: Approval & Rejection API

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **Security Specialist**,
I want to approve or reject pending routes,
So that I control what gets published to production (FR9, FR10, FR12).

## Acceptance Criteria

### AC1: Успешное одобрение маршрута
**Given** аутентифицированный пользователь с ролью security или admin
**And** маршрут в статусе `pending`
**When** POST `/api/v1/routes/{id}/approve`
**Then** статус маршрута меняется на `published`
**And** `approvedBy` устанавливается в id текущего пользователя
**And** `approvedAt` timestamp записывается
**And** событие cache invalidation публикуется в Redis
**And** response возвращает HTTP 200 с обновлённым маршрутом
**And** создаётся audit log entry: "route.approved"

### AC2: Автоматическая публикация после одобрения
**Given** маршрут одобрен
**When** gateway-core получает cache invalidation event
**Then** маршрут становится активным в течение 5 секунд (FR30, NFR3)
**And** запросы к path маршрута проксируются на upstream

### AC3: Успешное отклонение маршрута
**Given** аутентифицированный пользователь с ролью security или admin
**And** маршрут в статусе `pending`
**When** POST `/api/v1/routes/{id}/reject` с телом:
```json
{
  "reason": "Upstream URL points to internal service not allowed for external access"
}
```
**Then** статус маршрута меняется на `rejected`
**And** `rejectionReason` сохраняется
**And** `rejectedBy` и `rejectedAt` записываются
**And** response возвращает HTTP 200 с обновлённым маршрутом
**And** создаётся audit log entry: "route.rejected"

### AC4: Отклонение без причины
**Given** попытка отклонить маршрут без причины
**When** POST `/api/v1/routes/{id}/reject` с пустым reason
**Then** response возвращает HTTP 400 Bad Request
**And** detail: "Rejection reason is required"

### AC5: Недостаточно прав (Developer)
**Given** пользователь с ролью developer (не security/admin)
**When** попытка approve или reject
**Then** response возвращает HTTP 403 Forbidden

### AC6: Маршрут не в статусе pending
**Given** маршрут не в статусе `pending` (draft, published, rejected)
**When** попытка approve или reject
**Then** response возвращает HTTP 409 Conflict
**And** detail: "Only pending routes can be approved/rejected"

### AC7: Маршрут не найден
**Given** несуществующий route ID
**When** POST `/api/v1/routes/{id}/approve` или `/reject`
**Then** response возвращает HTTP 404 Not Found

## Tasks / Subtasks

### Backend: Approve Endpoint
- [x] **Task 1**: Добавить метод `approve` в `ApprovalService.kt` (AC: 1, 2, 5, 6)
  - [x] Метод `approve(routeId, userId, username): Mono<RouteResponse>`
  - [x] Проверка роли через @RequireRole или SecurityContext
  - [x] Проверка статуса (только PENDING)
  - [x] Установка status = PUBLISHED, approvedBy, approvedAt
  - [x] Публикация cache invalidation в Redis
  - [x] Интеграция с AuditService: "route.approved"
  - [x] Логирование через SLF4J с correlationId

- [x] **Task 2**: Добавить endpoint в `RouteController.kt` (AC: 1, 5, 6, 7)
  - [x] POST `/api/v1/routes/{id}/approve`
  - [x] Аннотация `@RequireRole(SECURITY)` или выше
  - [x] Получение userId из SecurityContext
  - [x] Вызов ApprovalService.approve()

### Backend: Reject Endpoint
- [x] **Task 3**: Добавить метод `reject` в `ApprovalService.kt` (AC: 3, 4, 5, 6)
  - [x] Метод `reject(routeId, userId, username, reason): Mono<RouteResponse>`
  - [x] Валидация reason (не пустая строка, не null)
  - [x] Проверка роли через @RequireRole или SecurityContext
  - [x] Проверка статуса (только PENDING)
  - [x] Установка status = REJECTED, rejectedBy, rejectedAt, rejectionReason
  - [x] Интеграция с AuditService: "route.rejected"
  - [x] Логирование через SLF4J с correlationId

- [x] **Task 4**: Добавить endpoint в `RouteController.kt` (AC: 3, 4, 5, 6, 7)
  - [x] POST `/api/v1/routes/{id}/reject`
  - [x] Request DTO: `RejectRouteRequest(reason: String)`
  - [x] Аннотация `@RequireRole(SECURITY)` или выше
  - [x] Валидация request body (@Valid, @NotBlank)
  - [x] Вызов ApprovalService.reject()

### Cache Invalidation (AC2)
- [x] **Task 5**: Реализовать cache invalidation для publish (AC: 2)
  - [x] Создать или обновить `RouteEventPublisher.kt`
  - [x] Публикация события в Redis pub/sub при approve
  - [x] gateway-core подписка на канал (если ещё не реализовано)
  - [x] Тест: маршрут доступен в gateway-core после approve

### Tests
- [x] **Task 6**: Unit tests для approve/reject в ApprovalService (AC: 1, 3, 4, 5, 6)
  - [x] `успешно одобряет pending маршрут`
  - [x] `устанавливает approvedBy и approvedAt при одобрении`
  - [x] `успешно отклоняет pending маршрут с причиной`
  - [x] `устанавливает rejectedBy, rejectedAt, rejectionReason при отклонении`
  - [x] `отклоняет approve для не-pending маршрута`
  - [x] `отклоняет reject для не-pending маршрута`
  - [x] `требует reason при отклонении`
  - [x] `создаёт audit log entry при одобрении`
  - [x] `создаёт audit log entry при отклонении`
  - [x] `публикует cache invalidation при approve`

- [x] **Task 7**: Integration tests для approve/reject endpoints (AC: 1-7)
  - [x] `POST approve возвращает 200 и обновляет статус на published`
  - [x] `POST approve устанавливает approvedBy и approvedAt`
  - [x] `POST reject возвращает 200 и обновляет статус на rejected`
  - [x] `POST reject сохраняет rejectionReason`
  - [x] `POST approve возвращает 403 для developer роли`
  - [x] `POST reject возвращает 403 для developer роли`
  - [x] `POST approve возвращает 409 для draft маршрута`
  - [x] `POST approve возвращает 409 для published маршрута`
  - [x] `POST reject возвращает 409 для draft маршрута`
  - [x] `POST reject возвращает 400 без reason`
  - [x] `POST reject возвращает 400 с пустым reason`
  - [x] `POST approve возвращает 404 для несуществующего маршрута`
  - [x] `POST reject возвращает 404 для несуществующего маршрута`

## Dev Notes

### Архитектурные требования

**Service Location (уже существует):**
```
backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/ApprovalService.kt
```

**Паттерн из Story 4.1:**
- ApprovalService уже содержит метод `submitForApproval()`
- Использовать тот же стиль: Reactive chains с Mono/Flux
- Проверка статуса через `route.status != RouteStatus.PENDING`
- Логирование через SLF4J с correlationId
- Интеграция с AuditService для всех изменений

### Существующий код для референса

**ApprovalService (Story 4.1):**
```kotlin
// Паттерн проверки статуса из submitForApproval()
if (route.status != RouteStatus.DRAFT) {
    return Mono.error(ConflictException("Only draft routes can be submitted for approval"))
}

// Для approve/reject изменить на:
if (route.status != RouteStatus.PENDING) {
    return Mono.error(ConflictException("Only pending routes can be approved/rejected"))
}
```

**Route Entity (уже обновлён в 4.1):**
```kotlin
// Поля уже добавлены в Story 4.1:
var approvedBy: UUID? = null
var approvedAt: Instant? = null
var rejectedBy: UUID? = null
var rejectedAt: Instant? = null
var rejectionReason: String? = null
```

**Role-Based Access Control (из Story 2.4):**
```kotlin
// Аннотация для ограничения доступа
@RequireRole(Role.SECURITY)  // security и admin могут доступ
fun approveRoute(...)
```

### Request/Response DTOs

**Reject Request DTO:**
```kotlin
// backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/RejectRouteRequest.kt
data class RejectRouteRequest(
    @field:NotBlank(message = "Rejection reason is required")
    val reason: String
)
```

**Success Response (200):**
```json
{
  "id": "uuid",
  "path": "/api/orders",
  "upstreamUrl": "http://order-service:8080",
  "methods": ["GET", "POST"],
  "status": "published",
  "approvedBy": "security-user-uuid",
  "approvedAt": "2026-02-18T11:00:00Z",
  "createdBy": "developer-uuid",
  "createdAt": "2026-02-18T09:00:00Z",
  "submittedAt": "2026-02-18T10:30:00Z"
}
```

**Error Response (409 Conflict):**
```json
{
  "type": "https://api.gateway/errors/conflict",
  "title": "Conflict",
  "status": 409,
  "detail": "Only pending routes can be approved/rejected",
  "instance": "/api/v1/routes/uuid/approve",
  "correlationId": "abc-123"
}
```

**Error Response (400 Bad Request - validation):**
```json
{
  "type": "https://api.gateway/errors/validation",
  "title": "Validation Error",
  "status": 400,
  "detail": "Rejection reason is required",
  "instance": "/api/v1/routes/uuid/reject",
  "correlationId": "abc-123"
}
```

### Cache Invalidation

**Redis Pub/Sub для gateway-core:**
```kotlin
// RouteEventPublisher.kt
@Component
class RouteEventPublisher(
    private val redisTemplate: ReactiveRedisTemplate<String, String>
) {
    // Канал для cache invalidation
    companion object {
        const val ROUTE_CACHE_CHANNEL = "route:cache:invalidate"
    }

    fun publishRouteChanged(routeId: UUID): Mono<Long> {
        return redisTemplate.convertAndSend(
            ROUTE_CACHE_CHANNEL,
            routeId.toString()
        )
    }
}
```

**Интеграция в ApprovalService:**
```kotlin
fun approve(routeId: UUID, userId: UUID, username: String): Mono<RouteResponse> {
    return routeRepository.findById(routeId)
        .switchIfEmpty(Mono.error(NotFoundException("Route not found")))
        .flatMap { route ->
            // Проверки статуса...
            route.status = RouteStatus.PUBLISHED
            route.approvedBy = userId
            route.approvedAt = Instant.now()
            routeRepository.save(route)
        }
        .flatMap { savedRoute ->
            // Публикация cache invalidation
            routeEventPublisher.publishRouteChanged(routeId)
                .thenReturn(savedRoute)
        }
        .flatMap { savedRoute ->
            // Audit log
            auditService.log(...)
                .thenReturn(RouteResponse.from(savedRoute))
        }
}
```

### Audit Log Entry

**Approve:**
```kotlin
auditService.log(
    entityType = "route",
    entityId = routeId.toString(),
    action = "approved",
    userId = userId,
    username = username,
    changes = mapOf(
        "oldStatus" to "pending",
        "newStatus" to "published",
        "approvedAt" to approvedAt.toString()
    )
)
```

**Reject:**
```kotlin
auditService.log(
    entityType = "route",
    entityId = routeId.toString(),
    action = "rejected",
    userId = userId,
    username = username,
    changes = mapOf(
        "oldStatus" to "pending",
        "newStatus" to "rejected",
        "rejectedAt" to rejectedAt.toString(),
        "rejectionReason" to reason
    )
)
```

### Тестирование

**Unit Test Location:**
```
backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/service/ApprovalServiceTest.kt
```
— Добавить тесты для approve() и reject() в существующий файл

**Integration Test Location:**
```
backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/ApprovalIntegrationTest.kt
```
— Добавить тесты для endpoints в существующий файл

**Testcontainers:**
- Использовать PostgreSQLContainer + RedisContainer для integration tests
- Паттерн из существующих тестов в ApprovalIntegrationTest.kt

### Previous Story Intelligence (Story 4.1)

**Из Story 4.1 (Submit for Approval API):**
- ApprovalService создан с методом submitForApproval()
- Паттерн ownership check и status check уже есть
- Route entity обновлён с approval fields
- RouteResponse включает submittedAt
- Integration tests используют Testcontainers

**Из Code Review Story 4.1:**
- Partial index в V6 миграции использует lowercase 'pending'
- AuditService интеграция через auditService.log()
- GlobalExceptionHandler корректно обрабатывает ConflictException

### Project Structure Notes

**Alignment:**
- Методы approve/reject в существующий ApprovalService — соответствует DRY
- RejectRouteRequest в dto/ — стандартная структура
- RouteEventPublisher в publisher/ или service/ — решение разработчика

**Detected Dependencies:**
- RouteEventPublisher может уже существовать из Story 1.5 (Configuration Hot-Reload)
- Проверить существование перед созданием

### References

- [Source: epics.md#Story-4.2] — Acceptance Criteria
- [Source: architecture.md#API-Patterns] — RFC 7807, REST conventions
- [Source: architecture.md#Cache-Strategy] — Redis pub/sub, cache invalidation
- [Source: 4-1-submit-approval-api.md#Dev-Notes] — ApprovalService паттерны
- [Source: CLAUDE.md] — Комментарии на русском, названия тестов на русском

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- Все тесты gateway-admin прошли успешно

### Completion Notes List

- Реализованы методы `approve()` и `reject()` в ApprovalService
- Создан RouteEventPublisher для cache invalidation через Redis Pub/Sub
- Обновлён RouteController с endpoints POST `/api/v1/routes/{id}/approve` и `/reject`
- Создан RejectRouteRequest DTO с валидацией @NotBlank
- Обновлён RouteResponse с полями approvedBy, approvedAt, rejectedBy, rejectedAt, rejectionReason
- Unit tests: 16 новых тестов для approve/reject в ApprovalServiceTest
- Integration tests: 17 новых тестов для approve/reject endpoints в ApprovalIntegrationTest
- Cache invalidation публикуется в Redis при одобрении маршрута (канал "route-cache-invalidation")
- Audit log записывается при approve ("route.approved") и reject ("route.rejected")

### Change Log

- 2026-02-18: Story 4.2 реализована — Approval & Rejection API
- 2026-02-18: Code Review (AI) — исправлены 7 issues
  - HIGH-1: Исправлен Redis канал (route:cache:invalidate → route-cache-invalidation)
  - HIGH-2: Добавлен integration test с RedisContainer для AC2
  - MEDIUM-1: RouteDetailResponse расширен approval fields
  - MEDIUM-2: Unit test approve — добавлена проверка содержимого audit log changes
  - MEDIUM-3: Unit test reject — добавлена проверка содержимого audit log changes
  - LOW-1: RouteWithCreator и RouteRepositoryCustomImpl — добавлены approval fields
  - LOW-2: Добавлен unit test для reject с rejected маршрутом

### File List

- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/ApprovalService.kt (modified)
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/RouteController.kt (modified)
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/RouteResponse.kt (modified)
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/RouteDetailResponse.kt (modified)
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/RejectRouteRequest.kt (new)
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/publisher/RouteEventPublisher.kt (new, fixed)
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/RouteRepositoryCustomImpl.kt (modified)
- backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/service/ApprovalServiceTest.kt (modified)
- backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/ApprovalIntegrationTest.kt (modified)
- backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/ApprovalRedisIntegrationTest.kt (new)

## Senior Developer Review (AI)

**Reviewer:** Claude Opus 4.5
**Date:** 2026-02-18
**Outcome:** ✅ APPROVED (after fixes)

### Review Summary

| Category | Issues Found | Fixed |
|----------|--------------|-------|
| HIGH | 2 | 2 ✅ |
| MEDIUM | 3 | 3 ✅ |
| LOW | 2 | 2 ✅ |

### Issues Found and Fixed

**HIGH-1: Redis канал не совпадает — AC2 не работал**
- Файл: `RouteEventPublisher.kt:26`
- Проблема: Канал `"route:cache:invalidate"` не совпадал с gateway-core `"route-cache-invalidation"`
- Исправление: Изменён канал на `"route-cache-invalidation"`

**HIGH-2: Integration tests не проверяли Redis pub/sub**
- Файл: `ApprovalIntegrationTest.kt`
- Исправление: Создан `ApprovalRedisIntegrationTest.kt` с RedisContainer

**MEDIUM-1: RouteDetailResponse не содержал approval fields**
- Файлы: `RouteDetailResponse.kt`, `RouteRepositoryCustomImpl.kt`
- Исправление: Добавлены approvedBy, approvedAt, rejectedBy, rejectedAt, rejectionReason

**MEDIUM-2/3: Unit tests не проверяли содержимое audit log changes**
- Файл: `ApprovalServiceTest.kt`
- Исправление: Добавлена проверка oldStatus, newStatus, approvedAt/rejectedAt

**LOW-1: RouteWithCreator без approval fields**
- Исправлено вместе с MEDIUM-1

**LOW-2: Отсутствовал unit test для reject с rejected маршрутом**
- Исправление: Добавлен тест `отклоняет reject для уже rejected маршрута`

### Test Results After Fixes

```
:gateway-admin:test — BUILD SUCCESSFUL
- ApprovalServiceTest: 32 tests passed ✅
```

### Positive Observations

1. ✅ ApprovalService — чистая реализация с правильным порядком проверок
2. ✅ RouteEventPublisher — graceful error handling с logging
3. ✅ Integration tests — полное покрытие AC1, AC3-AC7
4. ✅ RFC 7807 error responses — корректный формат
5. ✅ Комментарии и названия тестов на русском (CLAUDE.md compliance)
