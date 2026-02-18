# Story 4.1: Submit for Approval API

Status: done

## Story

As a **Developer**,
I want to submit my draft route for security approval,
So that it can be reviewed and published (FR7).

## Acceptance Criteria

### AC1: Успешная отправка на согласование
**Given** маршрут в статусе `draft`, владельцем которого является текущий пользователь
**When** POST `/api/v1/routes/{id}/submit`
**Then** статус маршрута меняется на `pending`
**And** `submittedAt` timestamp записывается
**And** response возвращает HTTP 200 с обновлённым маршрутом
**And** создаётся audit log entry: "route.submitted"

### AC2: Нельзя отправить не-draft маршрут
**Given** маршрут не в статусе `draft` (pending, published, rejected)
**When** POST `/api/v1/routes/{id}/submit`
**Then** response возвращает HTTP 409 Conflict
**And** detail: "Only draft routes can be submitted for approval"

### AC3: Нельзя отправить чужой маршрут
**Given** маршрут в статусе `draft`, владельцем которого является другой пользователь
**When** POST `/api/v1/routes/{id}/submit`
**Then** response возвращает HTTP 403 Forbidden
**And** detail: "You can only submit your own routes"

### AC4: Валидация перед отправкой
**Given** draft маршрут с невалидными данными (например, недоступный upstream URL)
**When** POST `/api/v1/routes/{id}/submit`
**Then** response возвращает HTTP 400 Bad Request
**And** validation errors перечислены в RFC 7807 формате

### AC5: Маршрут не найден
**Given** несуществующий route ID
**When** POST `/api/v1/routes/{id}/submit`
**Then** response возвращает HTTP 404 Not Found

## Tasks / Subtasks

### Prerequisite: Database Migration (из Story 4.4)
- [x] **Task 0.1**: Создать миграцию `V6__add_approval_fields.sql` (AC: 4.4)
  - [x] Добавить колонку `submitted_at` (TIMESTAMP WITH TIME ZONE, nullable)
  - [x] Добавить колонку `approved_by` (UUID, nullable, FK → users)
  - [x] Добавить колонку `approved_at` (TIMESTAMP WITH TIME ZONE, nullable)
  - [x] Добавить колонку `rejected_by` (UUID, nullable, FK → users)
  - [x] Добавить колонку `rejected_at` (TIMESTAMP WITH TIME ZONE, nullable)
  - [x] Добавить колонку `rejection_reason` (TEXT, nullable)

### Backend Model Updates
- [x] **Task 1**: Обновить `Route.kt` entity (AC: 1)
  - [x] Добавить поле `submittedAt: Instant?`
  - [x] Добавить поля `approvedBy`, `approvedAt`, `rejectedBy`, `rejectedAt`, `rejectionReason`
  - [x] Обновить `@Column` аннотации для snake_case mapping

### Submit Endpoint
- [x] **Task 2**: Создать `ApprovalService.kt` (AC: 1, 2, 3)
  - [x] Метод `submitForApproval(routeId, userId, username): Mono<RouteResponse>`
  - [x] Проверка ownership (createdBy == userId)
  - [x] Проверка статуса (только DRAFT)
  - [x] Установка status = PENDING, submittedAt = now()
  - [x] Интеграция с AuditService: "route.submitted"
  - [x] Логирование через SLF4J

- [x] **Task 3**: Добавить endpoint в `RouteController.kt` (AC: 1, 2, 3, 5)
  - [x] POST `/api/v1/routes/{id}/submit`
  - [x] Аннотация `@RequireRole(DEVELOPER)` или выше
  - [x] Получение userId из SecurityContext
  - [x] Вызов ApprovalService.submitForApproval()

- [x] **Task 4**: Валидация перед submit (AC: 4)
  - [x] Метод `validateRouteForSubmission(route): Mono<ValidationResult>`
  - [x] Проверка: path не пустой
  - [x] Проверка: upstreamUrl валидный URL формат
  - [x] Проверка: methods не пустой список
  - [x] Опционально: проверка доступности upstream (настраиваемая) — отложено, реализована базовая валидация

### Error Handling
- [x] **Task 5**: Обновить `GlobalExceptionHandler.kt` (AC: 2, 3, 4, 5)
  - [x] Убедиться, что ConflictException → 409 с RFC 7807
  - [x] Убедиться, что AccessDeniedException → 403 с RFC 7807
  - [x] Добавить correlationId во все error responses

### Tests
- [x] **Task 6**: Unit tests для ApprovalService (AC: 1, 2, 3)
  - [x] `успешно отправляет draft маршрут на согласование`
  - [x] `отклоняет отправку не-draft маршрута`
  - [x] `отклоняет отправку чужого маршрута`
  - [x] `создаёт audit log entry при успешной отправке`

- [x] **Task 7**: Integration tests для POST /submit (AC: 1-5)
  - [x] `POST submit возвращает 200 и обновляет статус на pending`
  - [x] `POST submit возвращает 409 для pending маршрута`
  - [x] `POST submit возвращает 403 для чужого маршрута`
  - [x] `POST submit возвращает 404 для несуществующего маршрута`
  - [x] `POST submit возвращает 400 для маршрута с невалидными данными`

## Dev Notes

### Архитектурные требования

**Service Location:**
```
backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/ApprovalService.kt
```

**Паттерн из существующего RouteService:**
- Reactive chains с Mono/Flux
- Проверка ownership через `route.createdBy != userId`
- Проверка статуса через `route.status != RouteStatus.DRAFT`
- Логирование через SLF4J с correlationId
- Интеграция с AuditService для всех изменений

### Существующий код для референса

**Route Entity (текущая):** `gateway-common/src/main/kotlin/com/company/gateway/common/model/Route.kt`
- Нужно добавить approval fields

**RouteService (паттерн):** `gateway-admin/src/main/kotlin/com/company/gateway/admin/service/RouteService.kt`
- Методы update() и delete() — примеры проверки ownership и статуса
- Интеграция с AuditService — auditService.logUpdated()

**RouteController (паттерн):** `gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/RouteController.kt`
- Получение userId из @AuthenticationPrincipal
- Паттерн endpoint с валидацией

### Database Migration

**Файл:** `backend/gateway-admin/src/main/resources/db/migration/V6__add_approval_fields.sql`

```sql
-- V3__add_approval_fields.sql
-- Добавляет поля для approval workflow (Epic 4)

ALTER TABLE routes
ADD COLUMN submitted_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN approved_by UUID REFERENCES users(id),
ADD COLUMN approved_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN rejected_by UUID REFERENCES users(id),
ADD COLUMN rejected_at TIMESTAMP WITH TIME ZONE,
ADD COLUMN rejection_reason TEXT;

-- Индекс для запросов pending approvals (Story 4.3)
CREATE INDEX idx_routes_status_submitted_at ON routes(status, submitted_at)
WHERE status = 'PENDING';

COMMENT ON COLUMN routes.submitted_at IS 'Время отправки на согласование';
COMMENT ON COLUMN routes.approved_by IS 'ID пользователя, одобрившего маршрут';
COMMENT ON COLUMN routes.approved_at IS 'Время одобрения';
COMMENT ON COLUMN routes.rejected_by IS 'ID пользователя, отклонившего маршрут';
COMMENT ON COLUMN routes.rejected_at IS 'Время отклонения';
COMMENT ON COLUMN routes.rejection_reason IS 'Причина отклонения';
```

### API Response Format

**Success (200):**
```json
{
  "id": "uuid",
  "path": "/api/orders",
  "upstreamUrl": "http://order-service:8080",
  "methods": ["GET", "POST"],
  "status": "pending",
  "submittedAt": "2026-02-18T10:30:00Z",
  "createdBy": "user-uuid",
  "createdAt": "2026-02-18T09:00:00Z"
}
```

**Error (409 Conflict):**
```json
{
  "type": "https://api.gateway/errors/conflict",
  "title": "Conflict",
  "status": 409,
  "detail": "Only draft routes can be submitted for approval",
  "instance": "/api/v1/routes/uuid/submit",
  "correlationId": "abc-123"
}
```

### Audit Log Entry

```kotlin
auditService.log(
    entityType = "route",
    entityId = routeId.toString(),
    action = "submitted",
    userId = userId,
    username = username,
    changes = mapOf(
        "oldStatus" to "draft",
        "newStatus" to "pending",
        "submittedAt" to submittedAt.toString()
    )
)
```

### Тестирование

**Unit Test Location:**
```
backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/service/ApprovalServiceTest.kt
```

**Integration Test Location:**
```
backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/ApprovalIntegrationTest.kt
```

**Testcontainers:**
- Использовать PostgreSQLContainer для integration tests
- Паттерн из RouteControllerIntegrationTest.kt

### Previous Story Intelligence (Epic 3)

**Из Story 3.1 (Route CRUD API):**
- Паттерн ownership check: `if (userRole == Role.DEVELOPER && route.createdBy != userId)`
- Паттерн status check: `if (route.status != RouteStatus.DRAFT)`
- AuditService integration: `auditService.logCreated()`, `auditService.logUpdated()`

**Из Story 3.2 (Route List API):**
- R2DBC маппинг: VARCHAR[] → List<String> требует кастомного маппинга
- COUNT(*) → использовать java.lang.Number

**Из Epic 3 Retrospective:**
- Action Item #1: Shared Elements Audit — ApprovalService будет shared между Stories 4.1, 4.2
- Action Item #2: docs/r2dbc-patterns.md — референс для маппинга новых полей

### Project Structure Notes

**Alignment:**
- ApprovalService в `service/` — соответствует architecture.md
- V3 миграция в `db/migration/` — Flyway паттерн
- Integration tests с Testcontainers — NFR требование

**Detected Issues:**
- Route.kt не имеет approval fields — нужно добавить
- AuditService не имеет метода logStatusChange() — нужно добавить или использовать logUpdated()

### References

- [Source: epics.md#Story-4.1] — Acceptance Criteria
- [Source: architecture.md#API-Patterns] — RFC 7807, REST conventions
- [Source: architecture.md#Data-Architecture] — PostgreSQL + R2DBC
- [Source: 3-1-route-crud-api.md#Dev-Notes] — Ownership и status check паттерны
- [Source: CLAUDE.md] — Комментарии на русском, названия тестов на русском

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

Все тесты прошли успешно:
- `./gradlew :gateway-admin:test` — 100% passed
- `./gradlew :gateway-core:test` — 100% passed

### Completion Notes List

1. **Task 0.1**: Создана миграция `V6__add_approval_fields.sql` (V6 вместо V3, так как V3-V5 уже заняты)
2. **Task 1**: Route.kt расширен 6 полями для approval workflow
3. **Task 2**: ApprovalService реализован с полной валидацией, ownership check и audit logging
4. **Task 3**: Endpoint POST `/api/v1/routes/{id}/submit` добавлен в RouteController
5. **Task 4**: Валидация встроена в ApprovalService.validateRouteForSubmission()
6. **Task 5**: GlobalExceptionHandler уже корректно обрабатывает все типы исключений — проверено
7. **Task 6**: Unit tests написаны (15 тестов в ApprovalServiceTest)
8. **Task 7**: Integration tests написаны (10 тестов в ApprovalIntegrationTest)

Дополнительные изменения:
- Обновлён RouteResponse для включения submittedAt
- Обновлён RouteDetailResponse и RouteWithCreator для submittedAt
- Обновлён RouteRepositoryCustomImpl для маппинга новых полей
- Добавлены миграции V5 и V6 для gateway-core тестов

### Change Log

- 2026-02-18: Реализован Submit for Approval API (Story 4.1)
  - Добавлена миграция V6 для approval workflow полей
  - Создан ApprovalService с методом submitForApproval()
  - Добавлен endpoint POST /api/v1/routes/{id}/submit
  - Написаны unit и integration тесты
- 2026-02-18: Code Review (AI) — исправлены 7 issues
  - HIGH-1: Исправлен partial index в V6 миграции ('PENDING' → 'pending')
  - HIGH-2: Добавлены 3 integration теста для AC4 (path, URL format, empty URL)
  - MEDIUM-1: Добавлены FK constraints в gateway-core тестовую миграцию
  - MEDIUM-2: Улучшен unit test — добавлена проверка содержимого audit log changes
  - MEDIUM-3: Обновлён Dev Notes — исправлен пример AuditService
  - LOW-1: Обновлён Dev Notes — исправлена версия миграции (V3 → V6)

## Senior Developer Review (AI)

**Reviewer:** Claude Opus 4.5
**Date:** 2026-02-18
**Outcome:** ✅ APPROVED (after fixes)

### Review Summary

| Category | Issues Found | Fixed |
|----------|--------------|-------|
| HIGH | 2 | 2 ✅ |
| MEDIUM | 3 | 3 ✅ |
| LOW | 2 | 1 ✅ |

### Issues Found and Fixed

**HIGH-1: Partial index в V6 миграции не работает**
- Файл: `V6__add_approval_fields.sql`
- Проблема: `WHERE status = 'PENDING'` не совпадает с lowercase данными
- Исправление: Изменено на `WHERE status = 'pending'`

**HIGH-2: Integration tests для AC4 неполные**
- Файл: `ApprovalIntegrationTest.kt`
- Проблема: Только 1 тест для валидации (empty methods)
- Исправление: Добавлены 3 теста — пустой path, невалидный URL, пустой URL

**MEDIUM-1: FK constraints отсутствуют в тестовой миграции**
- Файл: `gateway-core/.../V6__add_approval_fields.sql`
- Исправление: Добавлены `REFERENCES users(id)`

**MEDIUM-2: Unit test не проверяет содержимое audit log**
- Файл: `ApprovalServiceTest.kt`
- Исправление: Добавлена проверка oldStatus, newStatus, submittedAt

**MEDIUM-3: Dev Notes устаревшие**
- Файл: `4-1-submit-approval-api.md`
- Исправление: Обновлён пример AuditService.log()

**LOW-1: Dev Notes указывают неправильную версию миграции**
- Исправление: V3 → V6

**LOW-2: TODO placeholders в approve/reject endpoints (не исправлено)**
- Ожидаемо — будет реализовано в Story 4.2

### Positive Observations

1. ✅ ApprovalService — чистая реализация с правильным порядком проверок
2. ✅ Unit tests — полное покрытие всех edge cases (15 тестов)
3. ✅ Комментарии и названия тестов на русском (CLAUDE.md compliance)
4. ✅ RFC 7807 error responses — корректный формат
5. ✅ Audit logging — интегрировано правильно

### Test Results After Fixes

```
:gateway-admin:test — BUILD SUCCESSFUL
- ApprovalServiceTest: 15 tests passed ✅
- ApprovalIntegrationTest: 14 tests passed ✅
```

### File List

**Создано:**
- `backend/gateway-admin/src/main/resources/db/migration/V6__add_approval_fields.sql`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/ApprovalService.kt`
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/service/ApprovalServiceTest.kt`
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/ApprovalIntegrationTest.kt`
- `backend/gateway-core/src/test/resources/db/migration/V5__add_description_to_routes.sql`
- `backend/gateway-core/src/test/resources/db/migration/V6__add_approval_fields.sql`

**Модифицировано:**
- `backend/gateway-common/src/main/kotlin/com/company/gateway/common/model/Route.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/RouteController.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/RouteResponse.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/RouteDetailResponse.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/RouteRepositoryCustomImpl.kt`
