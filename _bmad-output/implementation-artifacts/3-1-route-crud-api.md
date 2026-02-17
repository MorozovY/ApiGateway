# Story 3.1: Route CRUD API

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **Developer**,
I want to create, update, and delete routes via API,
So that I can manage API routing configurations (FR1, FR2, FR3).

## Acceptance Criteria

1. **AC1: Создание маршрута**
   **Given** аутентифицированный developer
   **When** POST `/api/v1/routes` с валидными данными:
   ```json
   {
     "path": "/api/orders",
     "upstreamUrl": "http://order-service:8080",
     "methods": ["GET", "POST"],
     "description": "Order service endpoints"
   }
   ```
   **Then** response возвращает HTTP 201 Created
   **And** маршрут создаётся с `status: draft` и `createdBy: current_user_id`
   **And** response включает сгенерированные `id` и `createdAt`

2. **AC2: Обновление своего draft маршрута**
   **Given** маршрут в статусе draft, принадлежащий текущему пользователю
   **When** PUT `/api/v1/routes/{id}` с обновлёнными данными
   **Then** маршрут обновляется
   **And** `updatedAt` устанавливается на текущее время
   **And** response возвращает HTTP 200 с обновлённым маршрутом

3. **AC3: Запрет обновления не-draft маршрута**
   **Given** маршрут в статусе `published` или `pending`
   **When** PUT `/api/v1/routes/{id}` пытается обновить маршрут
   **Then** response возвращает HTTP 409 Conflict
   **And** detail: "Cannot edit route in current status"

4. **AC4: Удаление своего draft маршрута**
   **Given** маршрут в статусе draft, принадлежащий текущему пользователю
   **When** DELETE `/api/v1/routes/{id}`
   **Then** маршрут удаляется
   **And** response возвращает HTTP 204 No Content

5. **AC5: Запрет удаления не-draft маршрута**
   **Given** маршрут не в статусе draft
   **When** DELETE `/api/v1/routes/{id}` пытается удалить маршрут
   **Then** response возвращает HTTP 409 Conflict
   **And** detail: "Only draft routes can be deleted"

6. **AC6: Валидация входных данных**
   **Given** невалидные данные маршрута (path не начинается с /, невалидный URL)
   **When** POST или PUT запрос
   **Then** response возвращает HTTP 400 Bad Request
   **And** ошибки валидации возвращаются в формате RFC 7807

7. **AC7: Проверка уникальности path**
   **Given** маршрут с path `/api/orders` уже существует
   **When** POST `/api/v1/routes` с тем же path
   **Then** response возвращает HTTP 409 Conflict
   **And** detail: "Route with this path already exists"

## Tasks / Subtasks

- [x] **Task 1: Создать DTOs для Route API** (AC: #1, #2, #6)
  - [x] Subtask 1.1: Создать `CreateRouteRequest.kt` — данные для создания маршрута
  - [x] Subtask 1.2: Создать `UpdateRouteRequest.kt` — данные для обновления маршрута
  - [x] Subtask 1.3: Создать `RouteResponse.kt` — данные маршрута в ответе
  - [x] Subtask 1.4: Создать `RouteListResponse.kt` — пагинированный список маршрутов (для Story 3.2)

- [x] **Task 2: Расширить RouteRepository** (AC: #1, #7)
  - [x] Subtask 2.1: Добавить методы для пагинации (`findAllWithPagination(offset, limit)`)
  - [x] Subtask 2.2: Добавить подсчёт (`count()`, `countByStatus()`)
  - [x] Subtask 2.3: Добавить `existsByPath` для проверки уникальности

- [x] **Task 3: Реализовать RouteService** (AC: #1, #2, #3, #4, #5, #7)
  - [x] Subtask 3.1: Метод `create(request, userId)` — создание маршрута
  - [x] Subtask 3.2: Метод `update(id, request, userId)` — обновление с проверкой ownership и статуса
  - [x] Subtask 3.3: Метод `delete(id, userId)` — удаление с проверкой ownership и статуса
  - [x] Subtask 3.4: Метод `findById(id)` — получение маршрута по ID
  - [x] Subtask 3.5: Валидация уникальности path при создании и обновлении
  - [x] Subtask 3.6: Интеграция с AuditService для логирования операций

- [x] **Task 4: Обновить RouteController** (AC: #1, #2, #3, #4, #5, #6)
  - [x] Subtask 4.1: Реализовать `POST /api/v1/routes` — создание маршрута
  - [x] Subtask 4.2: Обновить `PUT /api/v1/routes/{id}` — полная реализация обновления
  - [x] Subtask 4.3: Обновить `DELETE /api/v1/routes/{id}` — полная реализация удаления
  - [x] Subtask 4.4: Добавить `GET /api/v1/routes/{id}` — получение маршрута по ID (подготовка для Story 3.3)
  - [x] Subtask 4.5: Добавить валидацию через `@Valid` и JSR-303 annotations
  - [x] Subtask 4.6: Сохранить существующую RBAC логику (Developer только свои, Admin/Security любые)

- [x] **Task 5: Добавить обработку новых исключений** (AC: #3, #5, #6, #7)
  - [x] Subtask 5.1: Использовать существующий `NotFoundException` для маршрутов
  - [x] Subtask 5.2: Использовать существующий `ConflictException` для статус/path конфликтов
  - [x] Subtask 5.3: Использовать существующий `GlobalExceptionHandler` — уже поддерживает RFC 7807

- [x] **Task 6: Создать интеграционные тесты** (AC: #1, #2, #3, #4, #5, #6, #7)
  - [x] Subtask 6.1: Тест создания маршрута с валидными данными
  - [x] Subtask 6.2: Тест создания маршрута с дублирующим path (409)
  - [x] Subtask 6.3: Тест создания маршрута с невалидными данными (400)
  - [x] Subtask 6.4: Тест обновления своего draft маршрута
  - [x] Subtask 6.5: Тест обновления non-draft маршрута (409)
  - [x] Subtask 6.6: Тест обновления чужого маршрута (403)
  - [x] Subtask 6.7: Тест удаления своего draft маршрута
  - [x] Subtask 6.8: Тест удаления non-draft маршрута (409)
  - [x] Subtask 6.9: Тест получения маршрута по ID (200 и 404)
  - [x] Subtask 6.10: Тест валидации description max length (400) [AI-Review]
  - [x] Subtask 6.11: Тест невалидного HTTP метода (400) [AI-Review]
  - [x] Subtask 6.12: Тест спецсимволов в path (400) [AI-Review]
  - [x] Subtask 6.13: Тест обновления/удаления REJECTED маршрута (409) [AI-Review]
  - [x] Subtask 6.14: Тест Admin UPDATE non-draft маршрута (409) [AI-Review]

- [x] **Task 7: Добавить миграцию для description поля** (AC: #1)
  - [x] Subtask 7.1: Создать `V5__add_description_to_routes.sql`
  - [x] Subtask 7.2: Обновить Route entity для поля description

## Dev Notes

### Previous Story Intelligence (Epic 2 — User Authentication & Access Control)

**Backend готов (из Epic 2):**
- User entity: `backend/gateway-common/src/main/kotlin/com/company/gateway/common/model/User.kt`
- UserRepository: `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/UserRepository.kt`
- Route entity: `backend/gateway-common/src/main/kotlin/com/company/gateway/common/model/Route.kt`
- RouteRepository (admin): `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/RouteRepository.kt`
- RouteController (placeholder): `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/RouteController.kt`
- OwnershipService: `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/OwnershipService.kt`
- AuditService: `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/AuditService.kt`
- @RequireRole annotation: `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/security/RequireRole.kt`
- SecurityContextUtils: `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/security/SecurityContextUtils.kt`
- ConflictException: `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/exception/ConflictException.kt`
- AccessDeniedException: `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/exception/AccessDeniedException.kt`
- GlobalExceptionHandler: `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/exception/GlobalExceptionHandler.kt`

**Database schema (из V2__create_routes.sql):**
```sql
CREATE TABLE routes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    path VARCHAR(500) NOT NULL UNIQUE,
    upstream_url VARCHAR(2000) NOT NULL,
    methods VARCHAR(100)[] DEFAULT '{}',
    status VARCHAR(20) NOT NULL DEFAULT 'draft',
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT routes_path_unique UNIQUE (path)
);
```

**Существующая Route entity:**
```kotlin
@Table("routes")
data class Route(
    @Id
    val id: UUID? = null,
    val path: String,
    @Column("upstream_url")
    val upstreamUrl: String,
    val methods: List<String> = emptyList(),
    val status: RouteStatus = RouteStatus.DRAFT,
    @Column("created_by")
    val createdBy: UUID? = null,
    @Column("created_at")
    val createdAt: Instant? = null,
    @Column("updated_at")
    val updatedAt: Instant? = null
)

enum class RouteStatus {
    DRAFT, PENDING, PUBLISHED, REJECTED
}
```

**ВАЖНО: Паттерн из Story 2.6 для UserService:**
- Использовать reactive `Mono`/`Flux` паттерны
- Валидация уникальности через `existsByX().flatMap { ... }`
- Аудит логирование через `AuditService.logX()`
- JSR-303 annotations для валидации DTO (`@NotBlank`, `@NotNull`, `@Pattern`)

---

### Architecture Compliance

**Из architecture.md — Backend:**

| Компонент | Требование |
|-----------|------------|
| **Controller** | `gateway-admin/controller/RouteController.kt` |
| **Service** | `gateway-admin/service/RouteService.kt` (NEW) |
| **DTOs** | `gateway-admin/dto/` — CreateRouteRequest, UpdateRouteRequest, RouteResponse |
| **Security** | `@RequireRole(DEVELOPER)` для CRUD, ownership проверка для update/delete |
| **Validation** | JSR-303 annotations (@NotBlank, @Pattern) |
| **Error Format** | RFC 7807 Problem Details |

**Naming Conventions (КРИТИЧНО):**
- Database columns: `snake_case` → `upstream_url`, `created_by`, `created_at`
- Kotlin properties: `camelCase` → `upstreamUrl`, `createdBy`, `createdAt`
- JSON fields: `camelCase` → `upstreamUrl`, `createdBy`, `createdAt`
- Используй `@Column("snake_case")` для маппинга

**API Response Formats:**

Single item response:
```json
{
  "id": "uuid",
  "path": "/api/orders",
  "upstreamUrl": "http://order-service:8080",
  "methods": ["GET", "POST"],
  "description": "Order service endpoints",
  "status": "draft",
  "createdBy": "user-uuid",
  "createdAt": "2026-02-17T10:30:00Z",
  "updatedAt": "2026-02-17T10:30:00Z"
}
```

Error response (RFC 7807):
```json
{
  "type": "https://api.gateway/errors/conflict",
  "title": "Conflict",
  "status": 409,
  "detail": "Route with this path already exists",
  "instance": "/api/v1/routes",
  "correlationId": "abc-123"
}
```

---

### Technical Requirements

**Валидация CreateRouteRequest:**
- `path`: required, must start with `/`, max 500 chars, regex `^/[a-zA-Z0-9/_-]*$`
- `upstreamUrl`: required, valid URL format, max 2000 chars
- `methods`: required, non-empty array, allowed values: GET, POST, PUT, DELETE, PATCH
- `description`: optional, max 1000 chars

**Валидация UpdateRouteRequest:**
- Те же правила, но все поля опциональные (partial update)

**Ownership логика (из Story 2.4):**
- Developer может создавать маршруты (createdBy = current user)
- Developer может update/delete только свои draft маршруты
- Security/Admin могут update/delete любые маршруты

**Audit logging:**
- `route.created` — при создании
- `route.updated` — при обновлении (с diff before/after)
- `route.deleted` — при удалении

---

### Project Structure Notes

**Существующая структура для RouteController:**
```
backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/
├── controller/
│   ├── RouteController.kt (MODIFY — заменить placeholder)
│   └── UserController.kt (reference)
├── service/
│   ├── RouteService.kt (NEW)
│   ├── OwnershipService.kt (EXISTS — использовать)
│   └── AuditService.kt (EXISTS — использовать)
├── dto/
│   ├── CreateRouteRequest.kt (NEW)
│   ├── UpdateRouteRequest.kt (NEW)
│   ├── RouteResponse.kt (NEW)
│   └── RouteListResponse.kt (NEW)
├── repository/
│   └── RouteRepository.kt (MODIFY — добавить методы)
└── exception/
    ├── ConflictException.kt (EXISTS)
    ├── AccessDeniedException.kt (EXISTS)
    └── GlobalExceptionHandler.kt (MODIFY если нужно)
```

**Миграции:**
- `V5__add_description_to_routes.sql` — добавить поле description

---

### Testing Standards

**Из CLAUDE.md и предыдущих stories:**
- Названия тестов ОБЯЗАТЕЛЬНО на русском языке
- Комментарии в коде на русском языке
- Testcontainers для PostgreSQL
- WebTestClient для integration tests
- Mock cookie auth через `mutateWith(mockJwt())`или прямое создание токена

**Пример паттерна тестов из Story 2.6:**
```kotlin
@Test
fun `создаёт маршрут и возвращает 201`() = runTest {
    // Arrange
    val request = CreateRouteRequest(
        path = "/api/orders",
        upstreamUrl = "http://order-service:8080",
        methods = listOf("GET", "POST"),
        description = "Order service"
    )

    // Act & Assert
    webTestClient.post()
        .uri("/api/v1/routes")
        .cookie("auth_token", developerToken)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus().isCreated
        .expectBody()
        .jsonPath("$.id").exists()
        .jsonPath("$.path").isEqualTo("/api/orders")
        .jsonPath("$.status").isEqualTo("draft")
        .jsonPath("$.createdBy").exists()
}
```

---

### Git Intelligence

**Последние коммиты Epic 2:**
- `ddebf94` feat: User Management Admin with CRUD operations (Story 2.6)
- `a4bb117` feat: Admin UI Login Page with authentication (Story 2.5)
- `a61844d` feat: Role-Based Access Control with hierarchy (Story 2.4)
- `ca3aa22` feat: Authentication middleware with JWT filter (Story 2.3)

**Паттерны из commits:**
- Commit message format: `feat: <что сделано> (Story X.Y)`
- Все changes в одном коммите на story (не разбивать)

---

### References

- [Source: architecture.md#API & Communication Patterns] — REST, RFC 7807, camelCase JSON
- [Source: architecture.md#Data Architecture] — PostgreSQL, R2DBC, snake_case columns
- [Source: epics.md#Story 3.1] — Acceptance Criteria
- [Source: CLAUDE.md] — Русские комментарии и названия тестов
- [Source: 2-6-user-management-admin.md] — Паттерн UserService для RouteService

---

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

N/A

### Completion Notes List

- Реализован полный Route CRUD API с проверкой ownership и статуса
- Все операции (create/update/delete) доступны только для draft маршрутов
- Developer может управлять только своими маршрутами, Admin/Security — любыми draft маршрутами
- Интеграция с AuditService для логирования всех операций
- Валидация через JSR-303 annotations с русскоязычными сообщениями об ошибках
- Обновлены существующие RBAC тесты для соответствия новой реализации (Story 3.1 изменила поведение для non-draft маршрутов)
- Все 289 тестов проходят успешно

### File List

**New files:**
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/CreateRouteRequest.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/UpdateRouteRequest.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/RouteResponse.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/RouteListResponse.kt
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/ValidHttpMethods.kt [AI-Review]
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/RouteService.kt
- backend/gateway-admin/src/main/resources/db/migration/V5__add_description_to_routes.sql
- backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/RouteControllerIntegrationTest.kt

**Modified files:**
- backend/gateway-common/src/main/kotlin/com/company/gateway/common/model/Route.kt (добавлено поле description, русские комментарии) [AI-Review]
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/RouteRepository.kt (добавлены методы пагинации, проверки уникальности, комментарии) [AI-Review]
- backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/RouteController.kt (полная реализация CRUD, TODO комментарии) [AI-Review]
- backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/RbacIntegrationTest.kt (обновлены тесты для соответствия Story 3.1)

### Review Follow-ups (AI)

- [ ] [AI-Review][LOW] RouteService — добавить correlationId в логи через MDC bridging с Reactor Context
- [ ] [AI-Review][LOW] RouteService.update — рассмотреть поддержку явного удаления description (установка в null)

## Change Log

| Date | Change | Author |
|------|--------|--------|
| 2026-02-17 | Initial implementation of Route CRUD API (Story 3.1) | Claude Opus 4.5 |
| 2026-02-17 | AI Code Review: добавлены комментарии, тесты, кастомный валидатор HTTP методов | Claude Opus 4.5 |

