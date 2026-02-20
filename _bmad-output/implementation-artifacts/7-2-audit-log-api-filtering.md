# Story 7.2: Audit Log API with Filtering

Status: done

## Story

As a **Security Specialist**,
I want to query and filter audit logs,
so that I can investigate changes and generate reports (FR22).

## Контекст реализации

**Зависимости от Story 7.1:**
- ✅ Таблица `audit_logs` расширена (V9__extend_audit_logs.sql) — ip_address, correlation_id
- ✅ AuditLog entity с полями: id, entityType, entityId, action, userId, username, changes, ipAddress, correlationId, createdAt
- ✅ AuditLogRepository с базовыми методами (findByEntityTypeAndEntityId, findByUserId, findByAction)
- ✅ AuditLogResponse DTO с UserInfo nested class
- ✅ AuditService с методами log(), logAsync(), logWithContext() и специализированными (logApproved, logRejected, logPublished)
- ✅ AuditController placeholder с @RequireRole(Role.SECURITY)

**Что нужно реализовать:**
1. Расширить AuditLogRepository custom query методами с фильтрацией и пагинацией
2. Создать AuditFilterRequest DTO для параметров фильтрации
3. Создать AuditListResponse DTO (или использовать PagedResponse<AuditLogResponse>)
4. Реализовать полноценный AuditController с фильтрацией
5. Unit и integration тесты

## Acceptance Criteria

**AC1 — Базовый список audit logs:**

**Given** an authenticated user with security or admin role
**When** GET `/api/v1/audit`
**Then** response returns paginated audit log entries:
```json
{
  "items": [
    {
      "id": "...",
      "entityType": "route",
      "entityId": "...",
      "action": "approved",
      "user": { "id": "...", "username": "dmitry" },
      "timestamp": "2026-02-11T14:30:00Z",
      "changes": { ... },
      "ipAddress": "192.168.1.100",
      "correlationId": "abc-123-def"
    }
  ],
  "total": 1250,
  "offset": 0,
  "limit": 50
}
```
**And** default sort is by timestamp descending (newest first)

**AC2 — Фильтрация по userId:**

**Given** query parameter `userId={uuid}`
**When** GET `/api/v1/audit?userId={uuid}`
**Then** only entries by that user are returned

**AC3 — Фильтрация по action:**

**Given** query parameter `action=approved`
**When** GET `/api/v1/audit?action=approved`
**Then** only approval entries are returned

**AC4 — Фильтрация по entityType:**

**Given** query parameter `entityType=route`
**When** GET `/api/v1/audit?entityType=route`
**Then** only route-related entries are returned

**AC5 — Фильтрация по диапазону дат:**

**Given** query parameters `dateFrom` and `dateTo`
**When** GET `/api/v1/audit?dateFrom=2026-02-01&dateTo=2026-02-11`
**Then** only entries within date range are returned
**And** dates are interpreted as start of day (00:00:00 UTC) for dateFrom
**And** dates are interpreted as end of day (23:59:59.999 UTC) for dateTo

**AC6 — Комбинирование фильтров:**

**Given** multiple filters combined
**When** GET `/api/v1/audit?entityType=route&action=rejected&userId={uuid}`
**Then** all filters are applied with AND logic

**AC7 — Контроль доступа:**

**Given** a user with developer role
**When** attempting to access `/api/v1/audit`
**Then** response returns HTTP 403 Forbidden

## Tasks / Subtasks

- [x] Task 1: Создать AuditFilterRequest DTO (AC1-AC6)
  - [x] Поля: userId, action, entityType, dateFrom, dateTo, offset, limit
  - [x] Default значения: offset=0, limit=50

- [x] Task 2: Расширить AuditLogRepository custom queries
  - [x] Добавить findAllWithFilters с динамическими WHERE clause
  - [x] Добавить countWithFilters для total count
  - [x] Использовать @Query с R2DBC для сложных запросов
  - [x] Сортировка по created_at DESC

- [x] Task 3: Создать AuditService.findAll() метод
  - [x] Принимает AuditFilterRequest
  - [x] Возвращает PagedResponse<AuditLogResponse>
  - [x] Преобразует AuditLog → AuditLogResponse через ObjectMapper

- [x] Task 4: Реализовать полный AuditController (AC1-AC7)
  - [x] GET /api/v1/audit с query parameters
  - [x] Валидация параметров (offset >= 0, 1 <= limit <= 100)
  - [x] @RequireRole(Role.SECURITY) для защиты endpoint
  - [x] Обработка дат в формате ISO (yyyy-MM-dd)

- [x] Task 5: Unit тесты AuditService
  - [x] Тест: findAll() без фильтров возвращает все записи с пагинацией
  - [x] Тест: findAll() с userId фильтром
  - [x] Тест: findAll() с action фильтром
  - [x] Тест: findAll() с entityType фильтром
  - [x] Тест: findAll() с dateFrom/dateTo фильтрами
  - [x] Тест: findAll() с комбинацией фильтров (AND logic)

- [x] Task 6: Integration тесты AuditController
  - [x] Тест: GET /api/v1/audit возвращает пагинированный список (AC1)
  - [x] Тест: фильтрация по userId (AC2)
  - [x] Тест: фильтрация по action (AC3)
  - [x] Тест: фильтрация по entityType (AC4)
  - [x] Тест: фильтрация по диапазону дат (AC5)
  - [x] Тест: комбинация фильтров (AC6)
  - [x] Тест: developer получает 403 (AC7)

## Dev Notes

### Текущая схема audit_logs

```sql
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    user_id UUID NOT NULL,
    username VARCHAR(255) NOT NULL,
    changes TEXT,  -- JSON
    ip_address VARCHAR(45),        -- Story 7.1
    correlation_id VARCHAR(128),   -- Story 7.1
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT
);

-- Индексы (уже существуют):
CREATE INDEX idx_audit_logs_entity_type ON audit_logs (entity_type);
CREATE INDEX idx_audit_logs_action ON audit_logs (action);
CREATE INDEX idx_audit_logs_user_id ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at);
CREATE INDEX idx_audit_logs_correlation_id ON audit_logs (correlation_id);
```

### AuditFilterRequest DTO

```kotlin
/**
 * Параметры фильтрации и пагинации для списка audit logs.
 *
 * Story 7.2: Audit Log API with Filtering.
 */
data class AuditFilterRequest(
    val userId: UUID? = null,
    val action: String? = null,
    val entityType: String? = null,
    val dateFrom: LocalDate? = null,
    val dateTo: LocalDate? = null,
    val offset: Int = 0,
    val limit: Int = 50
)
```

### Паттерн dynamic query в R2DBC

R2DBC не поддерживает нативный Criteria API как JPA. Для динамических запросов используем R2dbcEntityTemplate или raw SQL через DatabaseClient:

```kotlin
@Repository
class AuditLogRepositoryCustomImpl(
    private val databaseClient: DatabaseClient,
    private val r2dbcEntityTemplate: R2dbcEntityTemplate
) : AuditLogRepositoryCustom {

    override fun findAllWithFilters(filter: AuditFilterRequest): Flux<AuditLog> {
        val sql = StringBuilder("SELECT * FROM audit_logs WHERE 1=1")
        val params = mutableMapOf<String, Any>()

        filter.userId?.let {
            sql.append(" AND user_id = :userId")
            params["userId"] = it
        }
        filter.action?.let {
            sql.append(" AND action = :action")
            params["action"] = it
        }
        filter.entityType?.let {
            sql.append(" AND entity_type = :entityType")
            params["entityType"] = it
        }
        filter.dateFrom?.let {
            sql.append(" AND created_at >= :dateFrom")
            params["dateFrom"] = it.atStartOfDay().atZone(ZoneOffset.UTC).toInstant()
        }
        filter.dateTo?.let {
            sql.append(" AND created_at <= :dateTo")
            // End of day: 23:59:59.999999
            params["dateTo"] = it.plusDays(1).atStartOfDay().minusNanos(1).atZone(ZoneOffset.UTC).toInstant()
        }

        sql.append(" ORDER BY created_at DESC")
        sql.append(" LIMIT :limit OFFSET :offset")
        params["limit"] = filter.limit
        params["offset"] = filter.offset

        var spec = databaseClient.sql(sql.toString())
        params.forEach { (key, value) -> spec = spec.bind(key, value) }

        return spec.map { row, _ ->
            AuditLog(
                id = row.get("id", UUID::class.java),
                entityType = row.get("entity_type", String::class.java)!!,
                entityId = row.get("entity_id", String::class.java)!!,
                action = row.get("action", String::class.java)!!,
                userId = row.get("user_id", UUID::class.java)!!,
                username = row.get("username", String::class.java)!!,
                changes = row.get("changes", String::class.java),
                ipAddress = row.get("ip_address", String::class.java),
                correlationId = row.get("correlation_id", String::class.java),
                createdAt = row.get("created_at", Instant::class.java)
            )
        }.all()
    }

    override fun countWithFilters(filter: AuditFilterRequest): Mono<Long> {
        // Аналогичная логика, но SELECT COUNT(*)
    }
}
```

### Альтернатива: использование R2dbcEntityTemplate

```kotlin
fun findAllWithFilters(filter: AuditFilterRequest): Flux<AuditLog> {
    var criteria = Criteria.empty()

    filter.userId?.let { criteria = criteria.and("userId").`is`(it) }
    filter.action?.let { criteria = criteria.and("action").`is`(it) }
    filter.entityType?.let { criteria = criteria.and("entityType").`is`(it) }
    filter.dateFrom?.let {
        criteria = criteria.and("createdAt").greaterThanOrEquals(it.atStartOfDay().toInstant(ZoneOffset.UTC))
    }
    filter.dateTo?.let {
        criteria = criteria.and("createdAt").lessThanOrEquals(it.plusDays(1).atStartOfDay().minusNanos(1).toInstant(ZoneOffset.UTC))
    }

    return r2dbcEntityTemplate.select(AuditLog::class.java)
        .matching(Query.query(criteria)
            .sort(Sort.by(Sort.Direction.DESC, "createdAt"))
            .limit(filter.limit)
            .offset(filter.offset.toLong()))
        .all()
}
```

**Рекомендация:** Использовать R2dbcEntityTemplate с Criteria API — это более type-safe и менее подвержено SQL injection.

### AuditController паттерн

```kotlin
@RestController
@RequestMapping("/api/v1/audit")
@RequireRole(Role.SECURITY)
class AuditController(
    private val auditService: AuditService,
    private val objectMapper: ObjectMapper
) {
    companion object {
        const val MAX_LIMIT = 100
    }

    @GetMapping
    fun listAuditLogs(
        @RequestParam(required = false) userId: UUID?,
        @RequestParam(required = false) action: String?,
        @RequestParam(required = false) entityType: String?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateFrom: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) dateTo: LocalDate?,
        @RequestParam(defaultValue = "0") offset: Int,
        @RequestParam(defaultValue = "50") limit: Int
    ): Mono<ResponseEntity<PagedResponse<AuditLogResponse>>> {
        // Валидация
        if (offset < 0) {
            return Mono.error(ValidationException("Offset must be >= 0"))
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            return Mono.error(ValidationException("Limit must be between 1 and $MAX_LIMIT"))
        }

        val filter = AuditFilterRequest(
            userId = userId,
            action = action,
            entityType = entityType,
            dateFrom = dateFrom,
            dateTo = dateTo,
            offset = offset,
            limit = limit
        )

        return auditService.findAll(filter)
            .map { ResponseEntity.ok(it) }
    }
}
```

### Допустимые значения action

Из Story 7.1 и существующего кода:

| Action | Entity Type | Описание |
|--------|-------------|----------|
| created | route, rate_limit, user | Создание сущности |
| updated | route, rate_limit, user | Обновление сущности |
| deleted | route, rate_limit | Удаление сущности |
| route.submitted | route | Отправка на согласование |
| route.resubmitted | route | Повторная отправка |
| approved | route | Одобрение security |
| rejected | route | Отклонение security |
| published | route | Публикация после approve |
| role_changed | user | Смена роли пользователя |

**Примечание:** Для фильтрации по action не делаем валидацию на допустимые значения — просто возвращаем пустой список если action не найден.

### Допустимые значения entityType

| Entity Type | Описание |
|-------------|----------|
| route | Маршруты |
| rate_limit | Политики rate limiting |
| user | Пользователи |

### Project Structure Notes

**Новые файлы:**
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/AuditFilterRequest.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/AuditLogRepositoryCustom.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/AuditLogRepositoryCustomImpl.kt`
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/service/AuditServiceFilterTest.kt`
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/controller/AuditControllerTest.kt`

**Модифицируемые файлы:**
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/AuditLogRepository.kt` — extends AuditLogRepositoryCustom
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/AuditService.kt` — добавить findAll()
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/AuditController.kt` — полная реализация

### Testing Strategy

**Unit тесты:**
- Mock AuditLogRepository
- Verify filter application
- Verify pagination
- Verify sort order

**Integration тесты:**
- Testcontainers с PostgreSQL
- Seed test data с разными entityType, action, userId, timestamps
- Verify full flow через REST endpoint

### Reactive Patterns (из CLAUDE.md)

- НЕ использовать .block()
- Использовать Flux для списков, Mono для single values
- Использовать R2dbcEntityTemplate для type-safe queries
- Использовать .collectList() только когда нужен List в памяти

### Error Handling

- 400 Bad Request: невалидные параметры (offset < 0, limit > 100, invalid date format)
- 403 Forbidden: developer пытается получить доступ
- RFC 7807 формат для всех ошибок

### References

- [Source: backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/AuditLogRepository.kt]
- [Source: backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/AuditService.kt]
- [Source: backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/AuditController.kt]
- [Source: backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/AuditLogResponse.kt]
- [Source: backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/RouteController.kt] — паттерн фильтрации
- [Source: _bmad-output/implementation-artifacts/7-1-audit-log-entity-event-recording.md]
- [Source: epics.md#Story 7.2: Audit Log API with Filtering]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

N/A

### Completion Notes List

- Все AC (1-7) полностью реализованы и покрыты тестами
- Unit тесты: 14 тестов в AuditServiceFilterTest.kt
- Integration тесты: 19 тестов в AuditControllerIntegrationTest.kt с Testcontainers
- Code review исправления: DRY refactoring в repository, NPE guard в DTO, локализация сообщений

### File List

**Новые файлы:**
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/AuditFilterRequest.kt` — DTO для параметров фильтрации
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/AuditLogRepositoryCustom.kt` — интерфейс custom методов
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/AuditLogRepositoryCustomImpl.kt` — реализация с динамическими запросами
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/service/AuditServiceFilterTest.kt` — unit тесты (14 тестов)
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/AuditControllerIntegrationTest.kt` — integration тесты (19 тестов)

**Модифицированные файлы:**
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/AuditLogRepository.kt` — extends AuditLogRepositoryCustom
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/AuditService.kt` — добавлен findAll() метод
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/AuditController.kt` — полная реализация GET /api/v1/audit
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/AuditLogResponse.kt` — добавлен NPE guard в from()

## Change Log

| Дата | Автор | Изменение |
|------|-------|-----------|
| 2026-02-20 | Claude Opus 4.5 | Code Review: исправлены 7 issues (4 HIGH, 3 MEDIUM). DRY refactoring в AuditLogRepositoryCustomImpl, NPE guard в AuditLogResponse, локализация сообщений в AuditController. Все Tasks отмечены выполненными, File List заполнен. Тесты: 14 unit + 19 integration = BUILD SUCCESSFUL |
| 2026-02-20 | Claude Opus 4.5 | Code Review #2: исправлены 7 issues (3 MEDIUM, 4 LOW). M1: константы DEFAULT_LIMIT/MAX_LIMIT вынесены в AuditFilterRequest companion object. M2: добавлена валидация dateFrom <= dateTo. M3/L2/L3: добавлены integration тесты на SQL injection, невалидный UUID, limit=100, dateFrom > dateTo. Тесты: 14 unit + 23 integration = BUILD SUCCESSFUL |
