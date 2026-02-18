# Story 4.4: Route Status Tracking

Status: ready-for-dev

## Story

As a **Developer**,
I want to see the full status history of my routes,
So that I understand the approval process and any rejection reasons (FR11).

## Acceptance Criteria

**AC1 — Database migration (уже выполнена):**

> ⚠️ ВНИМАНИЕ: Миграция из описания эпика (`V3__add_approval_fields.sql`) была реализована как `V6__add_approval_fields.sql` в ходе Story 4.1. Все поля (submitted_at, approved_by, approved_at, rejected_by, rejected_at, rejection_reason) уже существуют в БД. Создавать новую миграцию НЕ нужно.

**AC2 — GET /api/v1/routes/{id} — данные о rejection:**

**Given** маршрут в статусе `rejected`
**When** GET `/api/v1/routes/{id}`
**Then** ответ содержит:
- `rejectionReason` (текст причины)
- `rejectorUsername` (username пользователя, отклонившего маршрут)
- `rejectedAt` (timestamp)

**AC3 — GET /api/v1/routes/{id} — данные об approval:**

**Given** маршрут в статусе `published`
**When** GET `/api/v1/routes/{id}`
**Then** ответ содержит:
- `approverUsername` (username пользователя, одобрившего маршрут)
- `approvedAt` (timestamp)

**AC4 — Повторная подача отклонённого маршрута:**

**Given** маршрут в статусе `rejected`, принадлежащий текущему пользователю
**When** POST `/api/v1/routes/{id}/submit`
**Then** статус маршрута меняется на `pending`
**And** поля `rejectionReason`, `rejectedBy`, `rejectedAt` очищаются (NULL)
**And** `submittedAt` обновляется до текущего времени
**And** создаётся запись в audit log: `"route.resubmitted"`
**And** ответ — HTTP 200 с обновлённым маршрутом

**Given** маршрут в статусе `rejected`, принадлежащий другому пользователю
**When** POST `/api/v1/routes/{id}/submit`
**Then** ответ — HTTP 403 Forbidden
**And** detail: "You can only submit your own routes"

**AC5 — Список маршрутов разработчика со статусами:**

**Given** разработчик делает запрос
**When** GET `/api/v1/routes?createdBy=me`
**Then** список содержит только его маршруты
**And** каждый маршрут включает поле `status` (draft / pending / published / rejected)
**And** пагинация работает корректно

## Tasks / Subtasks

- [ ] Task 1: Обновить SQL-запрос `findByIdWithCreator` (AC2, AC3)
  - [ ] Добавить LEFT JOIN с users для `approved_by` → `approver_username`
  - [ ] Добавить LEFT JOIN с users для `rejected_by` → `rejector_username`
  - [ ] Обновить маппинг в `RouteRepositoryCustomImpl`

- [ ] Task 2: Обновить DTO (AC2, AC3)
  - [ ] Добавить `approverUsername: String?` и `rejectorUsername: String?` в `RouteWithCreator`
  - [ ] Добавить `approverUsername: String?` и `rejectorUsername: String?` в `RouteDetailResponse`

- [ ] Task 3: Обновить `ApprovalService.submitForApproval()` (AC4)
  - [ ] Разрешить статус `REJECTED` → `PENDING` (в дополнение к `DRAFT`)
  - [ ] При реsubmission очищать: `rejectionReason`, `rejectedBy`, `rejectedAt`
  - [ ] Обновлять `submittedAt` до текущего времени
  - [ ] Создавать audit log с action `"route.resubmitted"` для REJECTED → PENDING
  - [ ] Создавать audit log с action `"route.submitted"` для DRAFT → PENDING (текущее поведение)

- [ ] Task 4: Верификация routes list API (AC5)
  - [ ] Убедиться, что `GET /api/v1/routes?createdBy=me` возвращает статус в ответе
  - [ ] Проверить корректность маппинга статусов в `RouteResponse`

- [ ] Task 5: Тесты
  - [ ] Интеграционный тест: GET /api/v1/routes/{id} — rejected маршрут содержит rejectorUsername
  - [ ] Интеграционный тест: GET /api/v1/routes/{id} — published маршрут содержит approverUsername
  - [ ] Интеграционный тест: повторная подача rejected маршрута очищает rejection-поля
  - [ ] Интеграционный тест: повторная подача rejected маршрута чужим пользователем → 403
  - [ ] Интеграционный тест: GET /api/v1/routes?createdBy=me возвращает статус
  - [ ] Unit-тест для ApprovalService — resubmission flow

## Dev Notes

### Что уже реализовано и НЕ нужно делать

1. **Миграция V6__add_approval_fields.sql** — уже применена. Все поля в таблице `routes` существуют.
2. **Route entity** — все поля (`submittedAt`, `approvedBy`, `approvedAt`, `rejectedBy`, `rejectedAt`, `rejectionReason`) уже в классе `Route.kt`.
3. **RouteResponse** — уже включает все approval-поля как `UUID?` и `Instant?`.
4. **GET /api/v1/routes?createdBy=me** — уже работает через `findAllWithFilters` с параметром `createdBy`. Поле `status` уже в `RouteResponse`.
5. **submit/approve/reject эндпоинты** — реализованы в Stories 4.1, 4.2, 4.3.

### Ключевые изменения

#### Task 1 — SQL-запрос с тремя JOIN

Текущий `findByIdWithCreator` имеет только JOIN на создателя:
```sql
SELECT r.*, u.username as creator_username
FROM routes r
LEFT JOIN users u ON r.created_by = u.id
WHERE r.id = :id
```

Нужно добавить JOIN-ы для `approved_by` и `rejected_by`:
```sql
SELECT r.id, r.path, r.upstream_url, r.methods, r.description,
       r.status, r.created_by, r.created_at, r.updated_at,
       r.submitted_at, r.approved_by, r.approved_at,
       r.rejected_by, r.rejected_at, r.rejection_reason,
       creator.username AS creator_username,
       approver.username AS approver_username,
       rejector.username AS rejector_username
FROM routes r
LEFT JOIN users creator  ON r.created_by  = creator.id
LEFT JOIN users approver ON r.approved_by = approver.id
LEFT JOIN users rejector ON r.rejected_by = rejector.id
WHERE r.id = :id
```

Использовать `DatabaseClient` — тот же паттерн, что и в `findPendingWithCreator`.

#### Task 2 — DTO

Файл `RouteWithCreator.kt` (внутри `RouteDetailResponse.kt`):
```kotlin
data class RouteWithCreator(
    // ... существующие поля ...
    val creatorUsername: String?,
    val approverUsername: String?,   // ДОБАВИТЬ
    val rejectorUsername: String?,   // ДОБАВИТЬ
    val rateLimitId: UUID? = null
) {
    fun toResponse() = RouteDetailResponse(
        // ... существующие маппинги ...
        approverUsername = approverUsername,   // ДОБАВИТЬ
        rejectorUsername = rejectorUsername,   // ДОБАВИТЬ
    )
}
```

Файл `RouteDetailResponse.kt`:
```kotlin
data class RouteDetailResponse(
    // ... существующие поля ...
    val approverUsername: String? = null,   // ДОБАВИТЬ
    val rejectorUsername: String? = null,   // ДОБАВИТЬ
)
```

#### Task 3 — ApprovalService.submitForApproval()

Текущая логика проверяет только `DRAFT`:
```kotlin
if (route.status != RouteStatus.DRAFT) {
    // → 409 Conflict
}
```

Новая логика должна обрабатывать оба статуса:
```kotlin
// Определяем действие и audit action по статусу
val auditAction = when (route.status) {
    RouteStatus.DRAFT     -> "route.submitted"
    RouteStatus.REJECTED  -> "route.resubmitted"
    else -> return Mono.error(ConflictException("Only draft or rejected routes can be submitted"))
}

// При resubmission — очищаем rejection-поля
val updatedRoute = route.copy(
    status = RouteStatus.PENDING,
    submittedAt = Instant.now(),
    // Для REJECTED: очищаем rejection-поля
    rejectionReason = null,
    rejectedBy = null,
    rejectedAt = null
)
```

> Проверка владения (`createdBy == userId`) остаётся для обоих статусов.

### Структура файлов для изменения

| Файл | Действие |
|------|---------|
| `backend/gateway-admin/src/main/kotlin/.../repository/RouteRepositoryCustomImpl.kt` | Обновить SQL в `findByIdWithCreator` |
| `backend/gateway-admin/src/main/kotlin/.../dto/RouteDetailResponse.kt` | Добавить `approverUsername`, `rejectorUsername` |
| `backend/gateway-admin/src/main/kotlin/.../service/ApprovalService.kt` | Обновить `submitForApproval` для REJECTED статуса |
| `backend/gateway-admin/src/test/kotlin/.../integration/RouteStatusTrackingIntegrationTest.kt` | Создать новый тестовый файл |

### Архитектурные требования

- **Reactive**: всё через `Mono`/`Flux`, без `.block()`
- **Комментарии**: только на русском языке
- **Названия тестов**: только на русском языке
- **Ошибки**: RFC 7807 через `ProblemDetail`
- **Audit log**: создавать через `AuditLogService` по паттерну из Stories 4.1–4.3
- **SQL injection**: только whitelist для sort-полей; для ID использовать bind parameters

### Паттерны из предыдущих историй

**Маппинг строки с тремя JOIN** (по образцу `findPendingWithCreator`):
```kotlin
.map { row ->
    RouteWithCreator(
        id = row.get("id", UUID::class.java)!!,
        // ... другие поля ...
        creatorUsername  = row.get("creator_username",  String::class.java),
        approverUsername = row.get("approver_username", String::class.java),
        rejectorUsername = row.get("rejector_username", String::class.java),
    )
}
```

**Audit log паттерн** (из ApprovalService):
```kotlin
auditLogService.log(
    userId = userId,
    username = username,
    action = auditAction,   // "route.submitted" или "route.resubmitted"
    resourceType = "route",
    resourceId = routeId.toString(),
    details = mapOf("path" to route.path, "status" to "pending")
)
```

**Обработка ошибок** (RFC 7807, из ApprovalService):
```kotlin
Mono.error(ResponseStatusException(HttpStatus.CONFLICT,
    "Only draft or rejected routes can be submitted for approval"))
```

### Тестовые данные

Для интеграционных тестов нужно создавать пользователей нескольких ролей:
- `developer` — подаёт маршруты
- `security` — одобряет/отклоняет
- `anotherDeveloper` — для тестов 403

Паттерн инициализации из `UserControllerIntegrationTest`:
```kotlin
// Создать пользователей через UserService или прямо в БД через repository
// Получить JWT для каждого через /api/v1/auth/login
```

### Ссылки на артефакты

- [Source: planning-artifacts/epics.md#Story-4.4]
- [Source: planning-artifacts/architecture.md#API-Patterns]
- [Source: implementation-artifacts/4-1-submit-approval-api.md] — паттерн ApprovalService
- [Source: implementation-artifacts/4-2-approval-rejection-api.md] — reject flow, audit log
- [Source: implementation-artifacts/4-3-pending-approvals-list-api.md] — findPendingWithCreator SQL паттерн с JOIN

### Project Structure Notes

```
backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/
├── controller/
│   └── RouteController.kt              # существующий — изменения не требуются
├── service/
│   └── ApprovalService.kt              # ИЗМЕНИТЬ — resubmission flow
├── repository/
│   ├── RouteRepositoryCustom.kt        # без изменений
│   └── RouteRepositoryCustomImpl.kt    # ИЗМЕНИТЬ — SQL запрос
└── dto/
    └── RouteDetailResponse.kt          # ИЗМЕНИТЬ — новые поля

backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/
└── RouteStatusTrackingIntegrationTest.kt   # СОЗДАТЬ
```

## Dev Agent Record

### Agent Model Used

{{agent_model_name_version}}

### Debug Log References

### Completion Notes List

### File List
