# Story 3.2: Route List API with Filtering & Search

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **Developer**,
I want to list and search routes with filters,
So that I can find specific routes quickly (FR4).

## Acceptance Criteria

1. **AC1: Базовый список маршрутов с пагинацией**
   **Given** аутентифицированный пользователь
   **When** GET `/api/v1/routes`
   **Then** response возвращает пагинированный список:
   ```json
   {
     "items": [...],
     "total": 156,
     "offset": 0,
     "limit": 20
   }
   ```
   **And** сортировка по умолчанию — `createdAt` descending

2. **AC2: Фильтрация по статусу**
   **Given** query parameter `status=draft`
   **When** GET `/api/v1/routes?status=draft`
   **Then** возвращаются только маршруты со статусом draft

3. **AC3: Фильтрация по автору (мои маршруты)**
   **Given** query parameter `createdBy=me`
   **When** GET `/api/v1/routes?createdBy=me`
   **Then** возвращаются только маршруты, созданные текущим пользователем

4. **AC4: Текстовый поиск**
   **Given** query parameter `search=order`
   **When** GET `/api/v1/routes?search=order`
   **Then** возвращаются маршруты, содержащие "order" в path или description
   **And** поиск case-insensitive

5. **AC5: Пагинация с offset и limit**
   **Given** query parameters `offset=20&limit=10`
   **When** GET `/api/v1/routes?offset=20&limit=10`
   **Then** возвращаются маршруты 21-30
   **And** `total` отражает полное количество маршрутов, соответствующих фильтрам

6. **AC6: Комбинация фильтров (AND логика)**
   **Given** несколько фильтров одновременно
   **When** GET `/api/v1/routes?status=published&search=api`
   **Then** фильтры применяются с AND логикой
   **And** возвращаются только маршруты, соответствующие ВСЕМ критериям

## Tasks / Subtasks

- [x] **Task 1: Расширить RouteRepository для фильтрации и поиска** (AC: #1, #2, #3, #4, #5, #6)
  - [x] Subtask 1.1: Добавить метод `findWithFilters(status, createdBy, search, offset, limit)` с динамической сборкой SQL
  - [x] Subtask 1.2: Добавить метод `countWithFilters(status, createdBy, search)` для подсчёта total
  - [x] Subtask 1.3: Использовать DatabaseClient с native SQL для case-insensitive поиска по ILIKE

- [x] **Task 2: Создать DTO для параметров фильтрации** (AC: #2, #3, #4, #5)
  - [x] Subtask 2.1: Создать `RouteFilterRequest.kt` — параметры фильтрации (status, createdBy, search, offset, limit)
  - [x] Subtask 2.2: Добавить валидацию: offset >= 0, limit 1-100, status из enum

- [x] **Task 3: Обновить RouteService для поддержки фильтрации** (AC: #1, #2, #3, #4, #5, #6)
  - [x] Subtask 3.1: Обновить метод `findAll()` для приёма RouteFilterRequest
  - [x] Subtask 3.2: Реализовать преобразование `createdBy=me` в userId текущего пользователя
  - [x] Subtask 3.3: Комбинировать фильтры с AND логикой

- [x] **Task 4: Обновить RouteController** (AC: #1, #2, #3, #4, #5, #6)
  - [x] Subtask 4.1: Обновить GET `/api/v1/routes` для приёма query parameters
  - [x] Subtask 4.2: Добавить query params: status, createdBy, search, offset, limit
  - [x] Subtask 4.3: Передавать userId для преобразования `createdBy=me`

- [x] **Task 5: Создать интеграционные тесты** (AC: #1, #2, #3, #4, #5, #6)
  - [x] Subtask 5.1: Тест базового списка с пагинацией (default offset=0, limit=20)
  - [x] Subtask 5.2: Тест фильтрации по status=draft
  - [x] Subtask 5.3: Тест фильтрации по status=published
  - [x] Subtask 5.4: Тест createdBy=me возвращает только свои маршруты
  - [x] Subtask 5.5: Тест search по path (case-insensitive)
  - [x] Subtask 5.6: Тест search по description (case-insensitive)
  - [x] Subtask 5.7: Тест пагинации offset=20, limit=10
  - [x] Subtask 5.8: Тест комбинации фильтров status + search
  - [x] Subtask 5.9: Тест total отражает отфильтрованное количество (не общее)
  - [x] Subtask 5.10: Тест невалидных значений (offset < 0, limit > 100) — 400

## Dev Notes

### Previous Story Intelligence (Story 3.1 — Route CRUD API)

**Реализовано в Story 3.1:**
- `RouteController.listRoutes()` — базовый endpoint с offset/limit пагинацией
- `RouteService.findAll(offset, limit)` — простой список без фильтрации
- `RouteRepository.findAllWithPagination(offset, limit)` — SQL query с LIMIT/OFFSET
- `RouteListResponse` — DTO с items, total, offset, limit

**Текущая реализация RouteRepository (из Story 3.1):**
```kotlin
@Query("SELECT * FROM routes ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
fun findAllWithPagination(offset: Int, limit: Int): Flux<Route>

fun countByStatus(status: RouteStatus): Mono<Long>
```

**Нужно добавить:**
- Динамическая фильтрация по status, createdBy, search
- Case-insensitive поиск по path и description через PostgreSQL ILIKE
- Подсчёт total с учётом фильтров

**Паттерны из Story 3.1:**
- Reactive `Mono`/`Flux` chains
- `@Query` с native SQL для сложных запросов
- Валидация через JSR-303 annotations

---

### Architecture Compliance

**Из architecture.md — Database Queries:**

| Компонент | Требование |
|-----------|------------|
| **PostgreSQL** | Использовать ILIKE для case-insensitive поиска |
| **R2DBC** | Native SQL через @Query для сложных запросов |
| **Пагинация** | OFFSET/LIMIT с total count |
| **Фильтрация** | AND логика для комбинации фильтров |

**Naming Conventions (КРИТИЧНО):**
- Database columns: `snake_case` → `created_by`, `created_at`
- Kotlin properties: `camelCase` → `createdBy`, `createdAt`
- JSON query params: `camelCase` → `createdBy`, `search`

**API Response Format (из architecture.md):**
```json
{
  "items": [...],
  "total": 156,
  "offset": 0,
  "limit": 20
}
```

**Query Parameters:**
- `status` — RouteStatus enum (draft, pending, published, rejected)
- `createdBy` — "me" или UUID пользователя (для Admin просмотр конкретного пользователя)
- `search` — строка для поиска (min 1 char, max 100 chars)
- `offset` — смещение (default 0, min 0)
- `limit` — количество (default 20, min 1, max 100)

---

### Technical Requirements

**Динамическая сборка SQL запроса:**

Для R2DBC с динамическими фильтрами рекомендуется использовать `DatabaseClient` или `R2dbcEntityTemplate` вместо `@Query`, так как @Query не поддерживает условную логику.

**Вариант 1: DatabaseClient (рекомендуется)**
```kotlin
@Repository
class RouteRepositoryCustomImpl(
    private val databaseClient: DatabaseClient
) : RouteRepositoryCustom {

    fun findWithFilters(
        status: RouteStatus?,
        createdBy: UUID?,
        search: String?,
        offset: Int,
        limit: Int
    ): Flux<Route> {
        val sql = StringBuilder("SELECT * FROM routes WHERE 1=1")
        val params = mutableMapOf<String, Any>()

        status?.let {
            sql.append(" AND status = :status")
            params["status"] = it.name.lowercase()
        }
        createdBy?.let {
            sql.append(" AND created_by = :createdBy")
            params["createdBy"] = it
        }
        search?.let {
            sql.append(" AND (path ILIKE :search OR description ILIKE :search)")
            params["search"] = "%$it%"
        }

        sql.append(" ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
        params["offset"] = offset
        params["limit"] = limit

        return databaseClient.sql(sql.toString())
            .bindValues(params)
            .map { row, _ -> mapRowToRoute(row) }
            .all()
    }
}
```

**Вариант 2: Отдельные методы в Repository**
```kotlin
@Query("SELECT * FROM routes WHERE status = :status ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
fun findByStatusWithPagination(status: RouteStatus, offset: Int, limit: Int): Flux<Route>

@Query("""
    SELECT * FROM routes
    WHERE (path ILIKE :search OR description ILIKE :search)
    ORDER BY created_at DESC LIMIT :limit OFFSET :offset
""")
fun findBySearchWithPagination(search: String, offset: Int, limit: Int): Flux<Route>
```

**Рекомендация:** Использовать Вариант 1 (DatabaseClient) для гибкости комбинирования фильтров.

**PostgreSQL ILIKE:**
- Case-insensitive поиск: `path ILIKE '%order%'`
- Экранирование спецсимволов: `%`, `_`, `\` должны быть escaped
- Рекомендуется добавить функцию экранирования для search input

---

### Project Structure Notes

**Файлы для создания/модификации:**
```
backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/
├── repository/
│   ├── RouteRepository.kt (EXISTS — добавить custom interface)
│   └── RouteRepositoryCustom.kt (NEW — интерфейс для кастомных методов)
│   └── RouteRepositoryCustomImpl.kt (NEW — реализация с DatabaseClient)
├── service/
│   └── RouteService.kt (MODIFY — обновить findAll)
├── controller/
│   └── RouteController.kt (MODIFY — добавить query params)
├── dto/
│   └── RouteFilterRequest.kt (NEW — параметры фильтрации)
└── test/kotlin/.../integration/
    └── RouteListFilteringIntegrationTest.kt (NEW)
```

**Важно для R2DBC Custom Repository:**
```kotlin
// RouteRepository должен наследовать custom interface
interface RouteRepository : R2dbcRepository<Route, UUID>, RouteRepositoryCustom

// Custom interface
interface RouteRepositoryCustom {
    fun findWithFilters(...): Flux<Route>
    fun countWithFilters(...): Mono<Long>
}

// Реализация должна иметь имя: {Interface}Impl
class RouteRepositoryCustomImpl : RouteRepositoryCustom { ... }
```

---

### Testing Standards

**Из CLAUDE.md и Story 3.1:**
- Названия тестов ОБЯЗАТЕЛЬНО на русском языке
- Комментарии в коде на русском языке
- Testcontainers для PostgreSQL
- WebTestClient для integration tests

**Тестовые данные (setup):**
```kotlin
// Создать 5+ маршрутов с разными статусами, авторами, paths
// - 2 маршрута текущего пользователя (draft, published)
// - 2 маршрута другого пользователя (draft, published)
// - 1 маршрут с "orders" в path
// - 1 маршрут с "orders" в description
```

**Пример теста:**
```kotlin
@Test
fun `фильтрует маршруты по статусу draft`() = runTest {
    // Arrange: создать маршруты с разными статусами

    // Act & Assert
    webTestClient.get()
        .uri("/api/v1/routes?status=draft")
        .cookie("auth_token", developerToken)
        .exchange()
        .expectStatus().isOk
        .expectBody()
        .jsonPath("$.items").isArray
        .jsonPath("$.items[*].status").value<List<String>> { statuses ->
            assertThat(statuses).allMatch { it == "draft" }
        }
        .jsonPath("$.total").value<Int> { total ->
            assertThat(total).isEqualTo(expectedDraftCount)
        }
}
```

---

### Git Intelligence

**Последний коммит Story 3.1:**
```
9fcd455 feat: Route CRUD API with ownership and status validation (Story 3.1)
```

**Файлы из Story 3.1 (reference):**
- RouteController.kt — endpoint structure
- RouteService.kt — service layer pattern
- RouteRepository.kt — repository with @Query
- RouteListResponse.kt — response DTO

**Commit message format:**
```
feat: Route List API with filtering and search (Story 3.2)

- Add status, createdBy, search query parameters
- Implement ILIKE-based case-insensitive search
- Add RouteRepositoryCustom with DatabaseClient
- Add comprehensive integration tests

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
```

---

### References

- [Source: architecture.md#Data Architecture] — PostgreSQL, R2DBC, ILIKE queries
- [Source: architecture.md#API & Communication Patterns] — REST pagination format
- [Source: epics.md#Story 3.2] — Acceptance Criteria
- [Source: CLAUDE.md] — Русские комментарии и названия тестов
- [Source: 3-1-route-crud-api.md] — Паттерны RouteService, RouteRepository

---

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- Исправлен маппинг PostgreSQL массива `methods` (VARCHAR[]) — заменён String на Array<String>
- Исправлен маппинг COUNT(*) — PostgreSQL возвращает BIGINT (OID 20), использован java.lang.Number для универсальности

### Completion Notes List

- ✅ Реализован RouteRepositoryCustom с интерфейсом для динамической фильтрации
- ✅ Реализован RouteRepositoryCustomImpl с DatabaseClient для построения SQL с переменным набором фильтров
- ✅ Добавлена функция escapeForIlike() для экранирования спецсимволов в поисковых запросах
- ✅ Создан RouteFilterRequest DTO с валидацией (offset >= 0, limit 1-100, search 1-100 символов)
- ✅ Обновлён RouteService с методом findAllWithFilters() и преобразованием createdBy=me в UUID
- ✅ Обновлён RouteController с query parameters: status, createdBy, search, offset, limit
- ✅ Добавлена inline валидация параметров в контроллере
- ✅ Создано 19 интеграционных тестов покрывающих все AC
- ✅ Все 315 тестов gateway-admin проходят (включая регрессионные)

### Change Log

- 2026-02-17: Реализована Story 3.2 — Route List API with Filtering & Search
  - Добавлены query parameters для GET /api/v1/routes: status, createdBy, search, offset, limit
  - Реализован case-insensitive поиск через PostgreSQL ILIKE
  - Добавлен RouteRepositoryCustom с DatabaseClient для динамических запросов
  - Создано 19 интеграционных тестов

- 2026-02-17: Code Review Fixes (AI Review)
  - **HIGH-1 Fixed**: Невалидный UUID в createdBy теперь возвращает пустой результат вместо всех маршрутов
  - **HIGH-2 Fixed**: Добавлены тесты на невалидный UUID и валидный UUID другого пользователя
  - **HIGH-3 Fixed**: Убран Thread.sleep() из тестов, используется явный createdAt timestamp
  - **MEDIUM-1 Fixed**: Ошибки валидации теперь возвращаются в RFC 7807 формате
  - **MEDIUM-2 Fixed**: Убрано дублирование валидации (JSR-303 аннотации удалены, валидация в контроллере)
  - **MEDIUM-4 Fixed**: Добавлены тесты на экранирование спецсимволов (%, _) в поиске
  - **MEDIUM-5 Fixed**: Убран unchecked cast в mapRowToRoute, используется безопасный маппинг
  - Добавлен ValidationException для RFC 7807 ответов
  - Добавлено логирование в findAllWithFilters()
  - Добавлены константы MAX_LIMIT и MAX_SEARCH_LENGTH

- 2026-02-17: Code Review #2 Fixes (AI Review)
  - **HIGH-1 Fixed**: Добавлен явный ESCAPE clause в ILIKE запросы для надёжного экранирования спецсимволов
  - **MEDIUM-2 Fixed**: Извлечён метод buildWhereClause() для устранения дублирования кода
  - **MEDIUM-3 Fixed**: Используется mapNotNull вместо map для фильтрации null элементов в массиве methods
  - **MEDIUM-4 Fixed**: Добавлен defaultIfEmpty(0L) в countWithFilters для defensive programming
  - **LOW-1 Fixed**: Обновлена KDoc документация RouteController с указанием всех Stories

### File List

**New Files:**
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/RouteRepositoryCustom.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/RouteRepositoryCustomImpl.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/RouteFilterRequest.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/exception/ValidationException.kt
- backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/RouteListFilteringIntegrationTest.kt

**Modified Files:**
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/RouteRepository.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/RouteService.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/RouteController.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/exception/GlobalExceptionHandler.kt
