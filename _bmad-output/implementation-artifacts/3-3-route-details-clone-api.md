# Story 3.3: Route Details & Clone API

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **Developer**,
I want to view route details and clone existing routes,
So that I can reuse configurations efficiently (FR5, FR6).

## Acceptance Criteria

1. **AC1: Получение деталей маршрута по ID**
   **Given** маршрут существует с id `abc-123`
   **When** GET `/api/v1/routes/abc-123`
   **Then** response возвращает HTTP 200 с полными деталями маршрута:
   - id, path, upstreamUrl, methods, description
   - status, createdBy, createdAt, updatedAt
   - rateLimitId (если назначен)
   - Полная информация о создателе (username, не только id)

2. **AC2: Маршрут не найден**
   **Given** маршрут не существует
   **When** GET `/api/v1/routes/nonexistent`
   **Then** response возвращает HTTP 404 Not Found
   **And** response в RFC 7807 формате с detail "Route not found"

3. **AC3: Клонирование маршрута**
   **Given** существующий маршрут с id `abc-123`
   **When** POST `/api/v1/routes/abc-123/clone`
   **Then** создаётся новый маршрут с:
   - Тем же path (с суффиксом "-copy" если конфликт)
   - Теми же upstreamUrl, methods, description
   - Status: draft
   - CreatedBy: текущий пользователь
   **And** response возвращает HTTP 201 с клонированным маршрутом

4. **AC4: Автоматическое разрешение конфликта path при клонировании**
   **Given** маршрут с path `/api/orders` существует
   **When** клонирование и `/api/orders-copy` тоже существует
   **Then** клонированный маршрут получает path `/api/orders-copy-2`
   **And** автоинкремент продолжается: `-copy-3`, `-copy-4` и т.д.

5. **AC5: Клонирование несуществующего маршрута**
   **Given** маршрут не существует
   **When** POST `/api/v1/routes/nonexistent/clone`
   **Then** response возвращает HTTP 404 Not Found
   **And** response в RFC 7807 формате

## Tasks / Subtasks

- [x] **Task 1: Добавить endpoint GET /api/v1/routes/{id}** (AC: #1, #2)
  - [x] Subtask 1.1: Добавить метод getById() в RouteController
  - [x] Subtask 1.2: Реализовать findByIdWithCreator() в RouteService для получения route + user info
  - [x] Subtask 1.3: Создать RouteDetailResponse DTO с информацией о создателе (creatorUsername)
  - [x] Subtask 1.4: Обработать 404 с RFC 7807 через ResourceNotFoundException

- [x] **Task 2: Добавить endpoint POST /api/v1/routes/{id}/clone** (AC: #3, #4, #5)
  - [x] Subtask 2.1: Добавить метод cloneRoute() в RouteController
  - [x] Subtask 2.2: Реализовать cloneRoute() в RouteService
  - [x] Subtask 2.3: Реализовать generateUniquePath() — логика разрешения конфликтов path
  - [x] Subtask 2.4: Добавить findByPathStartingWith() в RouteRepository для проверки существующих path

- [x] **Task 3: Расширить Route entity и Repository** (AC: #1, #3)
  - [x] Subtask 3.1: Добавить метод findByIdWithCreator() с JOIN к users таблице
  - [x] Subtask 3.2: Добавить метод existsByPath() для проверки уникальности
  - [x] Subtask 3.3: Добавить метод findByPathPattern() для поиска путей с pattern (LIKE)

- [x] **Task 4: Создать интеграционные тесты** (AC: #1, #2, #3, #4, #5)
  - [x] Subtask 4.1: Тест GET /api/v1/routes/{id} — успешное получение деталей
  - [x] Subtask 4.2: Тест GET /api/v1/routes/{id} — включает username создателя
  - [x] Subtask 4.3: Тест GET /api/v1/routes/{id} — 404 для несуществующего маршрута
  - [x] Subtask 4.4: Тест POST /api/v1/routes/{id}/clone — успешное клонирование
  - [x] Subtask 4.5: Тест POST /api/v1/routes/{id}/clone — path с суффиксом -copy
  - [x] Subtask 4.6: Тест POST /api/v1/routes/{id}/clone — конфликт разрешается до -copy-2
  - [x] Subtask 4.7: Тест POST /api/v1/routes/{id}/clone — createdBy = текущий пользователь
  - [x] Subtask 4.8: Тест POST /api/v1/routes/{id}/clone — status = draft
  - [x] Subtask 4.9: Тест POST /api/v1/routes/{id}/clone — 404 для несуществующего маршрута

## Dev Notes

### Previous Story Intelligence (Story 3.2 — Route List API with Filtering)

**Реализовано в Story 3.2:**
- `RouteRepositoryCustom` — кастомный интерфейс для сложных запросов с DatabaseClient
- `RouteRepositoryCustomImpl` — реализация с динамической сборкой SQL
- `RouteFilterRequest` — DTO с валидацией параметров
- `ValidationException` — RFC 7807 ошибки валидации
- `escapeForIlike()` — функция экранирования спецсимволов для PostgreSQL ILIKE

**Текущая структура RouteRepository:**
```kotlin
interface RouteRepository : R2dbcRepository<Route, UUID>, RouteRepositoryCustom {
    @Query("SELECT * FROM routes WHERE path = :path")
    fun findByPath(path: String): Mono<Route>

    @Query("SELECT COUNT(*) FROM routes")
    fun countAll(): Mono<Long>

    // ... другие методы
}
```

**Паттерны из Story 3.1 & 3.2:**
- `ResourceNotFoundException` для 404 ошибок (уже существует)
- RFC 7807 через `GlobalExceptionHandler`
- Reactive chains с `Mono`/`Flux`
- DatabaseClient для сложных SQL запросов

---

### Architecture Compliance

**Из architecture.md — API Response Patterns:**

**Single Item Response (для GET /routes/{id}):**
```json
{
  "id": "uuid",
  "path": "/api/orders",
  "upstreamUrl": "http://order-service:8080",
  "methods": ["GET", "POST"],
  "description": "Order service endpoints",
  "status": "published",
  "createdBy": "user-uuid",
  "creatorUsername": "maria",
  "createdAt": "2026-02-11T10:30:00Z",
  "updatedAt": "2026-02-11T10:35:00Z",
  "rateLimitId": null
}
```

**Clone Response (для POST /routes/{id}/clone):**
```json
{
  "id": "new-uuid",
  "path": "/api/orders-copy",
  "upstreamUrl": "http://order-service:8080",
  "methods": ["GET", "POST"],
  "description": "Order service endpoints",
  "status": "draft",
  "createdBy": "current-user-uuid",
  "creatorUsername": "developer",
  "createdAt": "2026-02-17T14:00:00Z",
  "updatedAt": "2026-02-17T14:00:00Z",
  "rateLimitId": null
}
```

**Error Response (RFC 7807):**
```json
{
  "type": "https://api.gateway/errors/not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "Route not found",
  "instance": "/api/v1/routes/nonexistent",
  "correlationId": "abc-123"
}
```

**Naming Conventions (КРИТИЧНО):**
- Database columns: `snake_case` → `created_by`, `created_at`, `rate_limit_id`
- Kotlin properties: `camelCase` → `createdBy`, `createdAt`, `rateLimitId`
- JSON fields: `camelCase` → `createdBy`, `creatorUsername`

---

### Technical Requirements

**1. Route Detail Response с Creator Info:**

Для получения username создателя нужен JOIN с таблицей users:

```kotlin
// RouteRepositoryCustom (добавить метод)
fun findByIdWithCreator(id: UUID): Mono<RouteWithCreator>

// RouteRepositoryCustomImpl
fun findByIdWithCreator(id: UUID): Mono<RouteWithCreator> {
    val sql = """
        SELECT r.*, u.username as creator_username
        FROM routes r
        LEFT JOIN users u ON r.created_by = u.id
        WHERE r.id = :id
    """
    return databaseClient.sql(sql)
        .bind("id", id)
        .map { row, _ -> mapRowToRouteWithCreator(row) }
        .one()
}
```

**2. Clone Path Generation:**

```kotlin
// RouteService
fun cloneRoute(routeId: UUID, currentUserId: UUID): Mono<Route> {
    return routeRepository.findById(routeId)
        .switchIfEmpty(Mono.error(ResourceNotFoundException("Route not found")))
        .flatMap { original ->
            generateUniquePath(original.path)
                .flatMap { newPath ->
                    val cloned = original.copy(
                        id = UUID.randomUUID(),
                        path = newPath,
                        status = RouteStatus.DRAFT,
                        createdBy = currentUserId,
                        createdAt = Instant.now(),
                        updatedAt = Instant.now()
                    )
                    routeRepository.save(cloned)
                }
        }
}

private fun generateUniquePath(originalPath: String): Mono<String> {
    val basePath = "${originalPath}-copy"
    return routeRepository.existsByPath(basePath)
        .flatMap { exists ->
            if (!exists) {
                Mono.just(basePath)
            } else {
                findNextAvailablePath(originalPath)
            }
        }
}

private fun findNextAvailablePath(originalPath: String): Mono<String> {
    // Найти все пути вида /api/orders-copy, /api/orders-copy-2, etc.
    // Вернуть следующий свободный номер
    val pattern = "$originalPath-copy%"
    return routeRepository.findByPathLike(pattern)
        .map { it.path }
        .collectList()
        .map { existingPaths ->
            val maxSuffix = existingPaths
                .mapNotNull { path ->
                    val regex = Regex("${Regex.escape(originalPath)}-copy(?:-(\\d+))?$")
                    regex.find(path)?.let { match ->
                        match.groupValues.getOrNull(1)?.toIntOrNull() ?: 1
                    }
                }
                .maxOrNull() ?: 1
            "$originalPath-copy-${maxSuffix + 1}"
        }
}
```

**3. Repository Methods to Add:**

```kotlin
// RouteRepository
@Query("SELECT COUNT(*) > 0 FROM routes WHERE path = :path")
fun existsByPath(path: String): Mono<Boolean>

// RouteRepositoryCustom
fun findByIdWithCreator(id: UUID): Mono<RouteWithCreator>
fun findByPathLike(pattern: String): Flux<Route>
```

---

### Project Structure Notes

**Файлы для создания/модификации:**
```
backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/
├── controller/
│   └── RouteController.kt (MODIFY — добавить getById и cloneRoute)
├── service/
│   └── RouteService.kt (MODIFY — добавить findByIdWithCreator, cloneRoute)
├── repository/
│   ├── RouteRepository.kt (MODIFY — добавить existsByPath)
│   ├── RouteRepositoryCustom.kt (MODIFY — добавить findByIdWithCreator, findByPathLike)
│   └── RouteRepositoryCustomImpl.kt (MODIFY — реализация новых методов)
├── dto/
│   └── RouteDetailResponse.kt (NEW — response с creatorUsername)
└── test/kotlin/.../integration/
    └── RouteDetailsCloneIntegrationTest.kt (NEW)
```

**RouteDetailResponse DTO:**
```kotlin
data class RouteDetailResponse(
    val id: UUID,
    val path: String,
    val upstreamUrl: String,
    val methods: List<String>,
    val description: String?,
    val status: RouteStatus,
    val createdBy: UUID,
    val creatorUsername: String?,  // Новое поле — username создателя
    val createdAt: Instant,
    val updatedAt: Instant,
    val rateLimitId: UUID?
)
```

---

### Testing Standards

**Из CLAUDE.md:**
- Названия тестов ОБЯЗАТЕЛЬНО на русском языке
- Комментарии в коде на русском языке
- Testcontainers для PostgreSQL
- WebTestClient для integration tests

**Тестовые данные (setup):**
```kotlin
// Создать 3+ маршрута:
// - route1: /api/orders (published, user1)
// - route2: /api/users (draft, user2)
// - route3: /api/orders-copy (draft, user1) — для теста конфликта
```

**Примеры тестов:**
```kotlin
@Test
fun `возвращает детали маршрута с username создателя`() = runTest {
    // Arrange: создать маршрут

    // Act & Assert
    webTestClient.get()
        .uri("/api/v1/routes/$routeId")
        .cookie("auth_token", developerToken)
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.id").isEqualTo(routeId.toString())
        .jsonPath("$.path").isEqualTo("/api/orders")
        .jsonPath("$.creatorUsername").isEqualTo("testuser")
}

@Test
fun `клонирует маршрут с суффиксом -copy при конфликте пути`() = runTest {
    // Arrange: создать маршрут с path /api/orders

    // Act & Assert
    webTestClient.post()
        .uri("/api/v1/routes/$routeId/clone")
        .cookie("auth_token", developerToken)
        .exchange()
        .expectStatus().isCreated
        .expectBody()
        .jsonPath("$.path").isEqualTo("/api/orders-copy")
        .jsonPath("$.status").isEqualTo("draft")
        .jsonPath("$.createdBy").isEqualTo(currentUserId.toString())
}

@Test
fun `возвращает 404 для несуществующего маршрута`() = runTest {
    val nonExistentId = UUID.randomUUID()

    webTestClient.get()
        .uri("/api/v1/routes/$nonExistentId")
        .cookie("auth_token", developerToken)
        .exchange()
        .expectStatus().isNotFound
        .expectBody()
        .jsonPath("$.type").isEqualTo("https://api.gateway/errors/not-found")
        .jsonPath("$.detail").isEqualTo("Route not found")
}
```

---

### Git Intelligence

**Последний коммит (Story 3.1):**
```
9fcd455 feat: Route CRUD API with ownership and status validation (Story 3.1)
```

**Релевантные файлы из предыдущих историй:**
- RouteController.kt — структура endpoints
- RouteService.kt — бизнес-логика
- RouteRepository.kt + RouteRepositoryCustom.kt — data access
- ResourceNotFoundException — уже существует для 404

**Commit message format:**
```
feat: Route Details and Clone API (Story 3.3)

- Add GET /api/v1/routes/{id} with creator username
- Add POST /api/v1/routes/{id}/clone with path conflict resolution
- Implement automatic -copy suffix generation
- Add comprehensive integration tests

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
```

---

### References

- [Source: architecture.md#API & Communication Patterns] — REST response format
- [Source: architecture.md#Naming Patterns] — camelCase для JSON
- [Source: epics.md#Story 3.3] — Acceptance Criteria (FR5, FR6)
- [Source: CLAUDE.md] — Русские комментарии и названия тестов
- [Source: 3-2-route-list-api-filtering-search.md] — Паттерны RouteRepositoryCustom
- [Source: 3-1-route-crud-api.md] — Паттерны RouteController, RouteService

---

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- Все интеграционные тесты прошли успешно (RouteDetailsCloneIntegrationTest)
- Регрессионные тесты прошли успешно (gateway-admin:test)

### Completion Notes List

- ✅ Реализован GET /api/v1/routes/{id} с полной информацией о маршруте и username создателя
- ✅ Реализован POST /api/v1/routes/{id}/clone с автоматической генерацией уникального path
- ✅ Алгоритм разрешения конфликтов: -copy → -copy-2 → -copy-3 и т.д.
- ✅ Клонированный маршрут получает status=draft и createdBy=текущий пользователь
- ✅ 404 ошибки возвращаются в RFC 7807 формате
- ✅ Создано 20+ интеграционных тестов покрывающих все AC

### Change Log

- 2026-02-17: Реализована Story 3.3 — Route Details & Clone API

- 2026-02-17: Code Review Fixes (AI Review)
  - **MEDIUM-1 Fixed**: Добавлен retry logic (Retry.max(3)) для обработки race condition при параллельном клонировании
  - **MEDIUM-2 Verified**: status: String consistent между RouteResponse и RouteDetailResponse (не проблема)
  - **LOW-1 Noted**: Дублирование RouteWithCreator/RouteDetailResponse — оставлено как есть (minor refactoring)
  - **LOW-2 Fixed**: Добавлен тест на клонирование маршрута с null description

### File List

**Новые файлы:**
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/RouteDetailResponse.kt
- backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/RouteDetailsCloneIntegrationTest.kt

**Изменённые файлы:**
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/RouteController.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/RouteService.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/RouteRepositoryCustom.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/RouteRepositoryCustomImpl.kt
