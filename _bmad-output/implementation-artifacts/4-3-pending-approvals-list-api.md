# Story 4.3: Pending Approvals List API

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **Security Specialist**,
I want to see all routes waiting for my approval,
So that I can process them efficiently (FR8).

## Acceptance Criteria

### AC1: Успешное получение списка pending маршрутов
**Given** аутентифицированный пользователь с ролью security или admin
**When** GET `/api/v1/routes/pending`
**Then** response возвращает список всех маршрутов со статусом `pending`
**And** сортировка по `submittedAt` ascending (oldest first — FIFO)
**And** каждый маршрут включает: id, path, upstreamUrl, methods, submittedAt, createdBy (с username)

### AC2: Сортировка по submittedAt descending
**Given** query parameter `sort=submittedAt:desc`
**When** GET `/api/v1/routes/pending?sort=submittedAt:desc`
**Then** маршруты отсортированы по newest first

### AC3: Пустой список pending маршрутов
**Given** нет pending маршрутов в системе
**When** GET `/api/v1/routes/pending`
**Then** response возвращает пустой список с `total: 0`

### AC4: Недостаточно прав (Developer)
**Given** аутентифицированный пользователь с ролью developer
**When** GET `/api/v1/routes/pending`
**Then** response возвращает HTTP 403 Forbidden

### AC5: Пагинация
**Given** более 20 pending маршрутов
**When** GET `/api/v1/routes/pending?offset=0&limit=10`
**Then** возвращаются первые 10 маршрутов
**And** `total` отражает полное количество pending маршрутов

## Tasks / Subtasks

### Backend: Pending Routes Endpoint
- [ ] **Task 1**: Добавить метод в `RouteRepository` (AC: 1, 2, 3, 5)
  - [ ] Метод `findByStatusPending(sort, offset, limit): Flux<RouteWithCreator>`
  - [ ] COUNT query для total: `countByStatusPending(): Mono<Long>`
  - [ ] R2DBC запрос с JOIN на users для получения username
  - [ ] Поддержка сортировки по submittedAt (asc/desc)

- [ ] **Task 2**: Добавить метод в `RouteService` (AC: 1, 2, 3, 5)
  - [ ] Метод `findPendingRoutes(sort, pageable): Mono<PagedResponse<RouteDetailResponse>>`
  - [ ] Парсинг sort parameter (submittedAt:asc, submittedAt:desc)
  - [ ] Default sort: submittedAt ascending (FIFO queue)

- [ ] **Task 3**: Добавить endpoint в `RouteController` (AC: 1, 2, 3, 4, 5)
  - [ ] GET `/api/v1/routes/pending`
  - [ ] Аннотация `@RequireRole(SECURITY)` — security и admin могут доступ
  - [ ] Query parameters: `sort`, `offset`, `limit`
  - [ ] Response: `PagedResponse<RouteDetailResponse>`

### Response DTO Enhancement
- [ ] **Task 4**: Проверить RouteDetailResponse (AC: 1)
  - [ ] Убедиться что включает: id, path, upstreamUrl, methods, submittedAt
  - [ ] Убедиться что включает createdBy с username (creator object)
  - [ ] Если нет — обновить DTO

### Tests
- [ ] **Task 5**: Unit tests для RouteService.findPendingRoutes (AC: 1, 2, 3)
  - [ ] `возвращает pending маршруты отсортированные по submittedAt asc по умолчанию`
  - [ ] `возвращает pending маршруты отсортированные по submittedAt desc`
  - [ ] `возвращает пустой список когда нет pending маршрутов`
  - [ ] `применяет пагинацию к результатам`
  - [ ] `включает информацию о создателе маршрута`

- [ ] **Task 6**: Integration tests для GET /pending endpoint (AC: 1-5)
  - [ ] `GET pending возвращает 200 со списком pending маршрутов`
  - [ ] `GET pending сортирует по submittedAt asc по умолчанию`
  - [ ] `GET pending с sort=submittedAt:desc сортирует по newest first`
  - [ ] `GET pending возвращает пустой список с total:0 когда нет pending`
  - [ ] `GET pending возвращает 403 для developer роли`
  - [ ] `GET pending применяет пагинацию`
  - [ ] `GET pending включает createdBy с username`

## Dev Notes

### Архитектурные требования

**Endpoint Location:**
```
backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/RouteController.kt
```

**Service Location:**
```
backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/RouteService.kt
```

**Repository Location:**
```
backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/RouteRepository.kt
backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/RouteRepositoryCustomImpl.kt
```

### Существующий код для референса

**RouteRepositoryCustomImpl (из Story 3.2):**
```kotlin
// Паттерн JOIN с users для получения username
// Использовать аналогичный подход для pending routes
fun findWithCreator(status: String?, ...): Flux<RouteWithCreator>
```

**RouteService (из Story 3.2):**
```kotlin
// Паттерн для пагинированного ответа
fun findAll(params: RouteQueryParams): Mono<PagedResponse<RouteDetailResponse>>
```

**PagedResponse (из архитектуры):**
```kotlin
data class PagedResponse<T>(
    val items: List<T>,
    val total: Long,
    val offset: Int,
    val limit: Int
)
```

### API Response Format

**Success (200) — со списком:**
```json
{
  "items": [
    {
      "id": "uuid-1",
      "path": "/api/orders",
      "upstreamUrl": "http://order-service:8080",
      "methods": ["GET", "POST"],
      "description": "Order service endpoints",
      "status": "pending",
      "submittedAt": "2026-02-17T10:30:00Z",
      "createdBy": "user-uuid",
      "createdAt": "2026-02-17T09:00:00Z",
      "creator": {
        "id": "user-uuid",
        "username": "maria"
      }
    },
    {
      "id": "uuid-2",
      "path": "/api/payments",
      "upstreamUrl": "http://payment-service:8080",
      "methods": ["POST"],
      "description": "Payment processing",
      "status": "pending",
      "submittedAt": "2026-02-17T11:00:00Z",
      "createdBy": "user-uuid-2",
      "createdAt": "2026-02-17T10:00:00Z",
      "creator": {
        "id": "user-uuid-2",
        "username": "alex"
      }
    }
  ],
  "total": 2,
  "offset": 0,
  "limit": 20
}
```

**Success (200) — пустой список:**
```json
{
  "items": [],
  "total": 0,
  "offset": 0,
  "limit": 20
}
```

**Error (403 Forbidden):**
```json
{
  "type": "https://api.gateway/errors/forbidden",
  "title": "Forbidden",
  "status": 403,
  "detail": "Insufficient permissions",
  "instance": "/api/v1/routes/pending",
  "correlationId": "abc-123"
}
```

### Sort Parameter

**Формат:** `field:direction`

**Поддерживаемые значения:**
- `submittedAt:asc` (default) — oldest first, FIFO queue
- `submittedAt:desc` — newest first

**Парсинг:**
```kotlin
fun parseSort(sort: String?): Pair<String, Sort.Direction> {
    if (sort.isNullOrBlank()) {
        return "submitted_at" to Sort.Direction.ASC
    }
    val parts = sort.split(":")
    val field = when(parts[0]) {
        "submittedAt" -> "submitted_at"
        else -> "submitted_at"
    }
    val direction = if (parts.getOrNull(1) == "desc") Sort.Direction.DESC else Sort.Direction.ASC
    return field to direction
}
```

### SQL Query

**Pending Routes Query:**
```sql
SELECT
    r.id, r.path, r.upstream_url, r.methods, r.description,
    r.status, r.submitted_at, r.created_by, r.created_at,
    r.approved_by, r.approved_at, r.rejected_by, r.rejected_at, r.rejection_reason,
    u.id as creator_id, u.username as creator_username
FROM routes r
JOIN users u ON r.created_by = u.id
WHERE r.status = 'pending'
ORDER BY r.submitted_at ASC
OFFSET :offset LIMIT :limit
```

**Count Query:**
```sql
SELECT COUNT(*) FROM routes WHERE status = 'pending'
```

### Partial Index (уже создан в V6)

В миграции `V6__add_approval_fields.sql` уже создан partial index:
```sql
CREATE INDEX idx_routes_status_submitted_at ON routes(status, submitted_at)
WHERE status = 'pending';
```
Этот индекс оптимизирует запросы к pending маршрутам.

### Previous Story Intelligence (Stories 4.1, 4.2)

**Из Story 4.1 (Submit for Approval API):**
- submittedAt записывается при submit
- Маршрут переходит в статус `pending`
- R2DBC маппинг уже настроен для новых полей

**Из Story 4.2 (Approval & Rejection API):**
- RouteDetailResponse уже включает approval fields
- RouteWithCreator включает creator info
- Pending маршруты могут быть approved или rejected

**Из Code Review Story 4.2:**
- RouteDetailResponse расширен: approvedBy, approvedAt, rejectedBy, rejectedAt, rejectionReason
- RouteWithCreator обновлён для approval fields

### Тестирование

**Unit Test Location:**
```
backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/service/RouteServiceTest.kt
```
— Добавить тесты для findPendingRoutes()

**Integration Test Location:**
```
backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/PendingRoutesIntegrationTest.kt
```
— Создать новый файл или добавить в существующий RouteControllerIntegrationTest

**Testcontainers:**
- PostgreSQLContainer для integration tests
- Паттерн из RouteControllerIntegrationTest.kt

### Git Intelligence

**Последние коммиты:**
- `22bbbd5 feat: Approval Workflow API with code review fixes (Stories 4.1, 4.2)` — approval workflow готов
- Cache invalidation через Redis Pub/Sub работает
- Approval/rejection endpoints реализованы

**Файлы затронутые в 4.2:**
- RouteController.kt — approve/reject endpoints
- ApprovalService.kt — approve/reject methods
- RouteDetailResponse.kt — approval fields
- RouteWithCreator — approval fields

### Project Structure Notes

**Alignment:**
- Endpoint в RouteController — соответствует структуре (routes-related)
- Query в RouteRepositoryCustomImpl — расширение существующего custom repository
- Tests в integration/ — стандартная структура

**Dependencies:**
- RouteRepositoryCustomImpl уже содержит JOIN логику
- PagedResponse уже используется в RouteService
- @RequireRole annotation готова для использования

### References

- [Source: epics.md#Story-4.3] — Acceptance Criteria
- [Source: architecture.md#API-Patterns] — RFC 7807, REST conventions, PagedResponse format
- [Source: architecture.md#Data-Architecture] — PostgreSQL + R2DBC
- [Source: 4-2-approval-rejection-api.md#Dev-Notes] — RouteDetailResponse с approval fields
- [Source: 4-1-submit-approval-api.md#Dev-Notes] — submittedAt field
- [Source: CLAUDE.md] — Комментарии на русском, названия тестов на русском

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-5-20250929

### Debug Log References

Нет критических issues. Исправлена предсуществующая проблема с RouteEventPublisher (Optional injection для Redis).

### Completion Notes List

- **AC1**: GET /api/v1/routes/pending возвращает список pending маршрутов отсортированный по submittedAt ASC (FIFO). Включает createdBy с creatorUsername через JOIN с users таблицей.
- **AC2**: Поддержка sort=submittedAt:desc для сортировки по newest first.
- **AC3**: Возвращает пустой список с total:0 когда нет pending маршрутов.
- **AC4**: @RequireRole(Role.SECURITY) на endpoint — Developer получает 403. Admin через иерархию ролей имеет доступ.
- **AC5**: Параметры offset/limit с валидацией (offset >= 0, limit 1-100). Total в response отражает полное количество.
- **Bugfix**: RouteEventPublisher изменён для использования Optional<ReactiveStringRedisTemplate> — исправлена предсуществующая проблема с интеграционными тестами без Redis.

### File List

- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/PagedResponse.kt` — новый generic DTO для пагинированных ответов
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/RouteRepositoryCustom.kt` — добавлены методы findPendingWithCreator и countPending
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/RouteRepositoryCustomImpl.kt` — реализация findPendingWithCreator и countPending
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/RouteService.kt` — добавлен метод findPendingRoutes с parseSort
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/RouteController.kt` — добавлен GET /api/v1/routes/pending endpoint
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/publisher/RouteEventPublisher.kt` — исправлен для optional Redis dependency
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/service/RouteServiceTest.kt` — 11 unit тестов для findPendingRoutes и parseSort
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/PendingRoutesIntegrationTest.kt` — 12 интеграционных тестов покрывающих все AC
