# Story 5.1: Rate Limit Policy CRUD API

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an **Admin**,
I want to create and manage rate limiting policies,
so that I can define reusable rate limit configurations (FR13, FR14).

## Acceptance Criteria

**AC1 — Миграция базы данных:**

**Given** gateway-admin приложение запускается
**When** Flyway выполняет миграции
**Then** миграция V7__create_rate_limits.sql создаёт таблицу `rate_limits`:
- `id` (UUID, primary key, default gen_random_uuid())
- `name` (VARCHAR(100), unique, not null)
- `description` (TEXT, nullable)
- `requests_per_second` (INTEGER, not null, CHECK > 0)
- `burst_size` (INTEGER, not null, CHECK > 0)
- `created_by` (UUID, FK to users, not null)
- `created_at` (TIMESTAMP WITH TIME ZONE, default CURRENT_TIMESTAMP)
- `updated_at` (TIMESTAMP WITH TIME ZONE, default CURRENT_TIMESTAMP)
**And** created_at/updated_at автоматически обновляются через trigger (аналогично routes)
**And** индекс на `name` для быстрого поиска
**And** все колонки имеют комментарии (COMMENT ON COLUMN)

**AC2 — Создание политики (Admin):**

**Given** аутентифицированный пользователь с ролью admin
**When** POST `/api/v1/rate-limits` с body:
```json
{
  "name": "standard",
  "description": "Standard rate limit for most services",
  "requestsPerSecond": 100,
  "burstSize": 150
}
```
**Then** политика создаётся
**And** response HTTP 201 Created с телом созданной политики:
```json
{
  "id": "uuid",
  "name": "standard",
  "description": "Standard rate limit for most services",
  "requestsPerSecond": 100,
  "burstSize": 150,
  "createdBy": "admin-uuid",
  "createdAt": "2026-02-18T12:00:00Z",
  "updatedAt": "2026-02-18T12:00:00Z"
}
```
**And** audit log entry создаётся: `ratelimit.created`

**AC3 — Валидация создания:**

**Given** невалидные данные для создания политики
**When** POST `/api/v1/rate-limits`:
- `name` пустое или null → 400 "Name is required"
- `name` уже существует → 409 "Rate limit policy with this name already exists"
- `requestsPerSecond` ≤ 0 → 400 "Requests per second must be positive"
- `burstSize` ≤ 0 → 400 "Burst size must be positive"
- `burstSize` < `requestsPerSecond` → 400 "Burst size must be at least equal to requests per second"
**Then** response HTTP 400 или 409 с RFC 7807 error body

**AC4 — Обновление политики (Admin):**

**Given** аутентифицированный пользователь с ролью admin
**And** существующая политика с id `abc-123`
**When** PUT `/api/v1/rate-limits/abc-123` с body:
```json
{
  "name": "standard-updated",
  "description": "Updated description",
  "requestsPerSecond": 200,
  "burstSize": 300
}
```
**Then** политика обновляется
**And** `updatedAt` устанавливается в текущее время
**And** response HTTP 200 с обновлённой политикой
**And** cache invalidation event публикуется в Redis для всех маршрутов, использующих эту политику
**And** audit log entry создаётся: `ratelimit.updated`

**AC5 — Удаление политики (Admin):**

**Given** аутентифицированный пользователь с ролью admin
**And** политика с id `abc-123` НЕ используется ни одним маршрутом
**When** DELETE `/api/v1/rate-limits/abc-123`
**Then** политика удаляется
**And** response HTTP 204 No Content
**And** audit log entry создаётся: `ratelimit.deleted`

**AC6 — Запрет удаления используемой политики:**

**Given** политика используется N маршрутами
**When** DELETE `/api/v1/rate-limits/{id}`
**Then** response HTTP 409 Conflict
**And** RFC 7807 body с detail: "Policy is in use by N routes"

**AC7 — Получение списка политик:**

**Given** аутентифицированный пользователь (любая роль)
**When** GET `/api/v1/rate-limits`
**Then** response HTTP 200 со списком всех политик:
```json
{
  "items": [
    {
      "id": "uuid",
      "name": "standard",
      "description": "...",
      "requestsPerSecond": 100,
      "burstSize": 150,
      "usageCount": 5,
      "createdBy": "admin-uuid",
      "createdAt": "...",
      "updatedAt": "..."
    }
  ],
  "total": 1,
  "offset": 0,
  "limit": 100
}
```
**And** `usageCount` показывает количество маршрутов, использующих политику

**AC8 — Получение политики по ID:**

**Given** аутентифицированный пользователь (любая роль)
**When** GET `/api/v1/rate-limits/{id}`
**Then** response HTTP 200 с деталями политики
**And** включает `usageCount`

**AC9 — 404 для несуществующей политики:**

**Given** политика с id не существует
**When** GET/PUT/DELETE `/api/v1/rate-limits/{nonexistent-id}`
**Then** response HTTP 404 Not Found
**And** RFC 7807 body с detail: "Rate limit policy not found"

**AC10 — 403 для non-admin (CUD операции):**

**Given** аутентифицированный пользователь с ролью developer или security
**When** POST/PUT/DELETE `/api/v1/rate-limits/...`
**Then** response HTTP 403 Forbidden

## Tasks / Subtasks

- [x] Task 1: Создать миграцию V7__create_rate_limits.sql (AC1)
  - [x] Таблица rate_limits со всеми колонками
  - [x] CHECK constraints для requests_per_second > 0, burst_size > 0
  - [x] UNIQUE constraint на name
  - [x] Foreign key на users(id)
  - [x] Trigger для updated_at (реиспользовать из V2)
  - [x] Индекс на name
  - [x] COMMENT ON COLUMN для всех колонок

- [x] Task 2: Создать entity RateLimit в gateway-common (AC1)
  - [x] Создать RateLimit.kt в common/model/
  - [x] @Table("rate_limits"), @Column для snake_case
  - [x] Все поля согласно миграции

- [x] Task 3: Создать RateLimitRepository (AC7, AC8)
  - [x] Интерфейс extends ReactiveCrudRepository
  - [x] findByName(name: String): Mono<RateLimit>
  - [x] existsByName(name: String): Mono<Boolean>
  - [x] countByRateLimitId(id: UUID): Mono<Long> — для usageCount

- [x] Task 4: Создать DTO для Rate Limits (AC2, AC4, AC7)
  - [x] CreateRateLimitRequest.kt (name, description?, requestsPerSecond, burstSize)
  - [x] UpdateRateLimitRequest.kt (name?, description?, requestsPerSecond?, burstSize?)
  - [x] RateLimitResponse.kt (все поля + usageCount)
  - [x] Валидации: @NotBlank name, @Min(1) для чисел

- [x] Task 5: Создать RateLimitService (AC2-AC9)
  - [x] create(): проверка уникальности name, валидация burst >= requests, сохранение
  - [x] update(): проверка существования, валидация, обновление
  - [x] delete(): проверка usageCount = 0, удаление
  - [x] findById(): с usageCount
  - [x] findAll(): пагинация, usageCount для каждой политики
  - [x] AuditService интеграция

- [x] Task 6: Обновить RateLimitController (AC2-AC10)
  - [x] Заменить placeholder реализации
  - [x] @RequireRole(Role.ADMIN) для POST/PUT/DELETE
  - [x] @RequireRole(Role.DEVELOPER) для GET (минимальная роль)
  - [x] RequestBody validation
  - [x] HTTP статусы: 201, 200, 204, 400, 403, 404, 409

- [x] Task 7: Cache invalidation при обновлении политики (AC4)
  - [x] При update политики публиковать Redis event
  - [x] Найти все маршруты с rateLimitId = id политики
  - [x] Для каждого опубликовать cache invalidation
  - [x] (Реиспользован RouteEventPublisher)

- [x] Task 8: Добавить rateLimitId в Route entity (подготовка для Story 5.2)
  - [x] Миграция V8__add_rate_limit_to_routes.sql
  - [x] ALTER TABLE routes ADD COLUMN rate_limit_id UUID REFERENCES rate_limits(id)
  - [x] Обновить Route.kt с новым полем

- [x] Task 9: Интеграционные тесты (AC1-AC10)
  - [x] Тест: создание политики — admin получает 201
  - [x] Тест: создание политики — developer получает 403
  - [x] Тест: дублирование name — 409
  - [x] Тест: невалидные данные — 400 с описанием ошибки
  - [x] Тест: обновление политики — 200
  - [x] Тест: удаление неиспользуемой политики — 204
  - [x] Тест: удаление используемой политики — 409
  - [x] Тест: список политик с usageCount — 200
  - [x] Тест: политика не найдена — 404

## Dev Notes

### Существующий код

`RateLimitController.kt` уже содержит placeholder реализацию с правильными аннотациями:
- `@RestController`, `@RequestMapping("/api/v1/rate-limits")`
- `@RequireRole(Role.DEVELOPER)` для GET
- `@RequireRole(Role.ADMIN)` для POST/PUT/DELETE
- Методы возвращают `Mono<ResponseEntity<...>>`

**Заменить** placeholder тела реальной логикой через RateLimitService.

### Паттерны из предыдущих историй

**RouteService.kt паттерн для сервиса:**
```kotlin
@Service
class RateLimitService(
    private val rateLimitRepository: RateLimitRepository,
    private val routeRepository: RouteRepository,  // для countByRateLimitId
    private val auditService: AuditService
) {
    private val logger = LoggerFactory.getLogger(RateLimitService::class.java)

    fun create(request: CreateRateLimitRequest, userId: UUID, username: String): Mono<RateLimitResponse> {
        return rateLimitRepository.existsByName(request.name)
            .flatMap { exists ->
                if (exists) {
                    Mono.error(ConflictException("Rate limit policy with this name already exists"))
                } else {
                    // Валидация: burstSize >= requestsPerSecond
                    if (request.burstSize < request.requestsPerSecond) {
                        return@flatMap Mono.error<RateLimit>(
                            ValidationException("Burst size must be at least equal to requests per second")
                        )
                    }

                    val rateLimit = RateLimit(
                        name = request.name,
                        description = request.description,
                        requestsPerSecond = request.requestsPerSecond,
                        burstSize = request.burstSize,
                        createdBy = userId,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now()
                    )
                    rateLimitRepository.save(rateLimit)
                }
            }
            .flatMap { saved ->
                auditService.logCreated(...).thenReturn(saved)
            }
            .map { RateLimitResponse.from(it, usageCount = 0) }
    }
}
```

**RouteController.kt паттерн для контроллера:**
```kotlin
@PostMapping
@RequireRole(Role.ADMIN)
@ResponseStatus(HttpStatus.CREATED)
fun createPolicy(@Valid @RequestBody request: CreateRateLimitRequest): Mono<RateLimitResponse> {
    return SecurityContextUtils.getCurrentUser()
        .flatMap { user ->
            rateLimitService.create(request, user.userId, user.username)
        }
}
```

**Миграция V6__add_approval_fields.sql паттерн:**
```sql
-- V7__create_rate_limits.sql
-- Создаёт таблицу для политик rate limiting (Epic 5)

CREATE TABLE rate_limits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    requests_per_second INTEGER NOT NULL CHECK (requests_per_second > 0),
    burst_size INTEGER NOT NULL CHECK (burst_size > 0),
    created_by UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Добавляем trigger для updated_at (аналогично routes)
CREATE TRIGGER update_rate_limits_updated_at
    BEFORE UPDATE ON rate_limits
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- Индекс для поиска по имени
CREATE INDEX idx_rate_limits_name ON rate_limits(name);

-- Комментарии
COMMENT ON TABLE rate_limits IS 'Политики rate limiting для маршрутов';
COMMENT ON COLUMN rate_limits.name IS 'Уникальное имя политики';
COMMENT ON COLUMN rate_limits.requests_per_second IS 'Лимит запросов в секунду';
COMMENT ON COLUMN rate_limits.burst_size IS 'Максимальный размер burst (>= requests_per_second)';
```

### usageCount — подсчёт использований

Для получения `usageCount` (количество маршрутов, использующих политику) нужен JOIN или subquery:

```kotlin
// В RateLimitRepository или RouteRepository
@Query("""
    SELECT COUNT(*) FROM routes WHERE rate_limit_id = :rateLimitId
""")
fun countByRateLimitId(rateLimitId: UUID): Mono<Long>
```

Или использовать `routeRepository.countByRateLimitId(id)` в сервисе при формировании ответа.

### Cache invalidation при update

При обновлении политики нужно инвалидировать кэш всех маршрутов, которые её используют:

```kotlin
fun update(id: UUID, request: UpdateRateLimitRequest, ...): Mono<RateLimitResponse> {
    return rateLimitRepository.findById(id)
        .switchIfEmpty(Mono.error(NotFoundException("Rate limit policy not found")))
        .flatMap { existing ->
            val updated = existing.copy(...)
            rateLimitRepository.save(updated)
        }
        .flatMap { saved ->
            // Инвалидируем кэш для всех маршрутов с этой политикой
            routeRepository.findByRateLimitId(id)
                .flatMap { route -> routeEventPublisher.publishRouteUpdated(route) }
                .then()
                .thenReturn(saved)
        }
        .flatMap { saved ->
            auditService.logUpdated(...).thenReturn(saved)
        }
        .flatMap { saved ->
            // Получаем usageCount
            routeRepository.countByRateLimitId(id).map { count ->
                RateLimitResponse.from(saved, usageCount = count)
            }
        }
}
```

### Важные детали

1. **Порядок миграций:** V6 уже занята (approval fields). Следующая — V7.

2. **Trigger update_updated_at_column** уже существует (создан в V2__add_updated_at_trigger.sql). Просто применить к новой таблице.

3. **Task 8 (rate_limit_id в routes)** — подготовка для Story 5.2. Можно включить в эту историю или вынести отдельно. Рекомендация: включить, чтобы usageCount работал.

4. **ValidationException** — проверить существование. Если нет, использовать `IllegalArgumentException` или создать.

5. **Тесты на русском языке:**
```kotlin
@Test
fun `создание политики возвращает 201 для admin`() { ... }

@Test
fun `создание политики возвращает 403 для developer`() { ... }

@Test
fun `дублирование имени политики возвращает 409`() { ... }
```

### Структура файлов для создания

| Файл | Действие |
|------|---------|
| `backend/gateway-admin/src/main/resources/db/migration/V7__create_rate_limits.sql` | СОЗДАТЬ |
| `backend/gateway-admin/src/main/resources/db/migration/V8__add_rate_limit_to_routes.sql` | СОЗДАТЬ |
| `backend/gateway-common/src/main/kotlin/com/company/gateway/common/model/RateLimit.kt` | СОЗДАТЬ |
| `backend/gateway-common/src/main/kotlin/com/company/gateway/common/model/Route.kt` | ИЗМЕНИТЬ — добавить rateLimitId |
| `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/CreateRateLimitRequest.kt` | СОЗДАТЬ |
| `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/UpdateRateLimitRequest.kt` | СОЗДАТЬ |
| `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/RateLimitResponse.kt` | СОЗДАТЬ |
| `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/RateLimitRepository.kt` | СОЗДАТЬ |
| `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/RateLimitService.kt` | СОЗДАТЬ |
| `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/RateLimitController.kt` | ИЗМЕНИТЬ — заменить placeholder |
| `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/RouteRepository.kt` | ИЗМЕНИТЬ — добавить countByRateLimitId |
| `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/controller/RateLimitControllerTest.kt` | СОЗДАТЬ |

### Project Structure Notes

```
backend/gateway-common/src/main/kotlin/com/company/gateway/common/model/
├── Route.kt          # ИЗМЕНИТЬ — добавить rateLimitId: UUID?
├── RateLimit.kt      # СОЗДАТЬ — новая entity

backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/
├── dto/
│   ├── CreateRateLimitRequest.kt   # СОЗДАТЬ
│   ├── UpdateRateLimitRequest.kt   # СОЗДАТЬ
│   └── RateLimitResponse.kt        # СОЗДАТЬ
├── repository/
│   ├── RouteRepository.kt          # ИЗМЕНИТЬ — countByRateLimitId
│   └── RateLimitRepository.kt      # СОЗДАТЬ
├── service/
│   └── RateLimitService.kt         # СОЗДАТЬ
└── controller/
    └── RateLimitController.kt      # ИЗМЕНИТЬ

backend/gateway-admin/src/main/resources/db/migration/
├── V7__create_rate_limits.sql      # СОЗДАТЬ
└── V8__add_rate_limit_to_routes.sql # СОЗДАТЬ
```

### Архитектурные требования

- **Reactive patterns**: Mono/Flux chains, без .block()
- **RFC 7807**: все ошибки через GlobalExceptionHandler
- **snake_case**: колонки БД (`requests_per_second`, `burst_size`)
- **camelCase**: JSON поля (`requestsPerSecond`, `burstSize`)
- **Audit logging**: все CUD операции логируются через AuditService
- **@RequireRole**: ADMIN для CUD, DEVELOPER для R
- **Комментарии**: только на русском языке
- **Названия тестов**: только на русском языке
- **Testcontainers**: для интеграционных тестов

### References

- [Source: planning-artifacts/epics.md#Story-5.1] — Story requirements и AC
- [Source: planning-artifacts/architecture.md#Data-Architecture] — PostgreSQL, R2DBC, snake_case
- [Source: planning-artifacts/architecture.md#API-Format] — REST, RFC 7807, camelCase JSON
- [Source: backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/RouteService.kt] — паттерн сервиса
- [Source: backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/RateLimitController.kt] — placeholder для замены
- [Source: backend/gateway-admin/src/main/resources/db/migration/V6__add_approval_fields.sql] — паттерн миграции
- [Source: backend/gateway-common/src/main/kotlin/com/company/gateway/common/model/Route.kt] — паттерн entity

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-5-20250929

### Debug Log References

### Completion Notes List

- Реализованы все 9 задач из истории 5.1
- RateLimitService использует reactive chains (без .block())
- Cache invalidation реализован через существующий RouteEventPublisher
- existsByNameAndIdNot() добавлен в RateLimitRepository для проверки уникальности при обновлении
- countByRateLimitId() и findByRateLimitId() добавлены в RouteRepository
- ValidationException (burstSize < requestsPerSecond) возвращает 400 через GlobalExceptionHandler
- Тесты RbacIntegrationTest обновлены для корректной проверки RBAC с телом запроса

### File List

| Файл | Действие |
|------|---------|
| `backend/gateway-admin/src/main/resources/db/migration/V7__create_rate_limits.sql` | СОЗДАН |
| `backend/gateway-admin/src/main/resources/db/migration/V8__add_rate_limit_to_routes.sql` | СОЗДАН |
| `backend/gateway-common/src/main/kotlin/com/company/gateway/common/model/RateLimit.kt` | СОЗДАН |
| `backend/gateway-common/src/main/kotlin/com/company/gateway/common/model/Route.kt` | ИЗМЕНЁН — добавлено поле rateLimitId |
| `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/CreateRateLimitRequest.kt` | СОЗДАН |
| `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/UpdateRateLimitRequest.kt` | СОЗДАН |
| `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/RateLimitResponse.kt` | СОЗДАН |
| `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/RateLimitRepository.kt` | СОЗДАН |
| `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/RouteRepository.kt` | ИЗМЕНЁН — добавлены countByRateLimitId, findByRateLimitId |
| `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/RateLimitService.kt` | СОЗДАН |
| `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/RateLimitController.kt` | ИЗМЕНЁН — заменена placeholder реализация |
| `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/RateLimitControllerIntegrationTest.kt` | СОЗДАН |
| `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/RbacIntegrationTest.kt` | ИЗМЕНЁН — обновлены тесты AC9 для работы с реальной реализацией |

## Change Log

| Дата | Изменение |
|------|-----------|
| 2026-02-18 | Story 5.1 реализована: Rate Limit Policy CRUD API — миграции V7/V8, entity RateLimit, RateLimitRepository, DTO, RateLimitService, RateLimitController (замена placeholder), cache invalidation через RouteEventPublisher, интеграционные тесты (472 тестов, все проходят) |