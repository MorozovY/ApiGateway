# Story 6.5.1: Metrics API — Role-Based Filtering

Status: done

## Story

As a **Developer**,
I want the metrics top-routes endpoint to automatically filter routes I created,
so that I only see metrics for my own routes without seeing other teams' data.

## Acceptance Criteria

**AC1 — Автоматическая фильтрация для developer роли:**

**Given** пользователь с ролью `developer` вызывает `GET /api/v1/metrics/top-routes`
**When** запрос обрабатывается
**Then** возвращаются только маршруты где `route.createdBy = currentUser.id`
**And** фильтрация происходит автоматически на основе JWT токена

**AC2 — Полный доступ для admin/security:**

**Given** пользователь с ролью `admin` или `security` вызывает `GET /api/v1/metrics/top-routes`
**When** запрос обрабатывается
**Then** возвращаются все маршруты без фильтрации (текущее поведение)

**AC3 — Сохранение существующего API контракта:**

**Given** любой пользователь вызывает `GET /api/v1/metrics/top-routes`
**When** запрос обрабатывается
**Then** формат ответа остаётся неизменным (массив TopRouteDto объектов)
**And** существующие параметры `by` и `limit` работают как прежде

## Tasks / Subtasks

- [x] Task 1: Модифицировать MetricsController (AC1, AC2)
  - [x] Добавить `@AuthenticationPrincipal principal: JwtAuthenticationToken` в getTopRoutes()
  - [x] Извлечь userId и role из principal.claims
  - [x] Передать ownerId в MetricsService (UUID или null)

- [x] Task 2: Обновить MetricsService.getTopRoutes() (AC1, AC3)
  - [x] Добавить параметр `ownerId: UUID? = null`
  - [x] Если ownerId указан — фильтровать topRouteIds по createdBy из RouteRepository
  - [x] Если ownerId = null — возвращать все (текущее поведение)

- [x] Task 3: Интеграционные тесты (AC1, AC2, AC3)
  - [x] Тест: developer видит только свои маршруты
  - [x] Тест: admin видит все маршруты
  - [x] Тест: security видит все маршруты
  - [x] Тест: формат ответа не изменился

- [x] Task 4: Unit тесты MetricsService
  - [x] Тест: getTopRoutes с ownerId фильтрует результаты
  - [x] Тест: getTopRoutes без ownerId возвращает все

## Dev Notes

### Архитектурный контекст

Story 6.5.1 — hotfix story для разблокировки AC6 в Story 6.5:
- **Story 6.3** (done) — создан REST API `/api/v1/metrics/*` без role-based filtering
- **Story 6.5** (in-progress) — Admin UI требует фильтрацию для developer роли
- **Story 6.5.1** (current) — добавление фильтрации в backend

### Текущий код MetricsController (из Story 6.3)

**Файл:** `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/MetricsController.kt`

```kotlin
@GetMapping("/top-routes")
fun getTopRoutes(
    @RequestParam(defaultValue = "requests") by: String,
    @RequestParam(defaultValue = "10") limit: Int
): Mono<List<TopRouteDto>> {
    logger.debug("GET /metrics/top-routes?by={}&limit={}", by, limit)

    val sortBy = validateSortBy(by)
    val validLimit = validateLimit(limit)
    return metricsService.getTopRoutes(sortBy, validLimit)
}
```

### Требуемые изменения MetricsController

```kotlin
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
// или если используется custom principal:
// import com.company.gateway.admin.security.UserPrincipal

@GetMapping("/top-routes")
fun getTopRoutes(
    @RequestParam(defaultValue = "requests") by: String,
    @RequestParam(defaultValue = "10") limit: Int,
    @AuthenticationPrincipal jwt: Jwt
): Mono<List<TopRouteDto>> {
    logger.debug("GET /metrics/top-routes?by={}&limit={}", by, limit)

    val sortBy = validateSortBy(by)
    val validLimit = validateLimit(limit)

    // Извлекаем роль и userId из JWT claims
    val role = jwt.claims["role"] as? String
    val userIdStr = jwt.claims["userId"] as? String

    // Для developer фильтруем по owner, для admin/security — без фильтра
    val ownerId = if (role == "developer" && userIdStr != null) {
        UUID.fromString(userIdStr)
    } else {
        null
    }

    return metricsService.getTopRoutes(sortBy, validLimit, ownerId)
}
```

### Текущий код MetricsService.getTopRoutes()

**Файл:** `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/MetricsService.kt`

```kotlin
fun getTopRoutes(sortBy: MetricsSortBy, limit: Int): Mono<List<TopRouteDto>> {
    // ... собирает метрики по route_id из MeterRegistry ...

    val topRouteIds = sorted.take(limit).map { it.key }

    // Загружаем path из DB для каждого route_id
    return if (topRouteIds.isEmpty()) {
        Mono.just(emptyList())
    } else {
        val uuids = topRouteIds.mapNotNull { ... }

        routeRepository.findAllById(uuids)
            .collectMap({ it.id.toString() }, { it.path })
            .map { pathMap ->
                sorted.take(limit).map { (routeId, data) ->
                    // ... создаёт TopRouteDto ...
                }
            }
    }
}
```

### Требуемые изменения MetricsService

```kotlin
/**
 * Получает топ маршрутов по указанному критерию.
 *
 * @param sortBy критерий сортировки (requests, latency, errors)
 * @param limit максимальное количество маршрутов
 * @param ownerId если указан — фильтрует только маршруты созданные этим пользователем
 * @return Mono со списком топ-маршрутов
 */
fun getTopRoutes(sortBy: MetricsSortBy, limit: Int, ownerId: UUID? = null): Mono<List<TopRouteDto>> {
    logger.debug("Получение топ-{} маршрутов по: {}, ownerId: {}", limit, sortBy.value, ownerId)

    // ... существующий код сбора метрик ...

    val topRouteIds = sorted.take(limit).map { it.key }

    return if (topRouteIds.isEmpty()) {
        Mono.just(emptyList())
    } else {
        val uuids = topRouteIds.mapNotNull { ... }

        // Фильтруем по owner если указан
        val routesMono = if (ownerId != null) {
            routeRepository.findAllById(uuids)
                .filter { it.createdBy == ownerId }  // Фильтрация по owner
                .collectMap({ it.id.toString() }, { it.path })
        } else {
            routeRepository.findAllById(uuids)
                .collectMap({ it.id.toString() }, { it.path })
        }

        routesMono.map { pathMap ->
            // Фильтруем sorted чтобы включать только маршруты которые прошли фильтр
            sorted.filter { (routeId, _) -> pathMap.containsKey(routeId) }
                .take(limit)
                .map { (routeId, data) ->
                    // ... создаёт TopRouteDto ...
                }
        }
    }
}
```

### Проверка Route entity

Убедись что Route entity имеет поле `createdBy`:

```kotlin
// В com.company.gateway.common.model.Route или com.company.gateway.admin.model.Route
data class Route(
    val id: UUID,
    val path: String,
    val createdBy: UUID,  // <- это поле должно существовать
    // ...
)
```

Если поля нет — нужно проверить Story 3.1 (Route CRUD API) где оно должно было быть добавлено.

### JWT Claims структура

Из Story 2.2 (JWT Authentication Service), JWT содержит:
```json
{
  "sub": "username",
  "userId": "uuid-string",
  "role": "developer|security|admin",
  "exp": 1234567890
}
```

### Важные паттерны из CLAUDE.md

- **Reactive patterns:** Все методы возвращают `Mono<T>` или `Flux<T>`
- **Комментарии:** На русском языке
- **Названия тестов:** На русском языке
- **Error handling:** RFC 7807 формат

### Тестирование

**Unit тест MetricsService:**
```kotlin
@Test
fun `getTopRoutes с ownerId фильтрует результаты`() {
    // Given: маршруты созданные developer1 и developer2
    val developer1Id = UUID.randomUUID()
    val route1 = createRoute(id = UUID.randomUUID(), createdBy = developer1Id)
    val route2 = createRoute(id = UUID.randomUUID(), createdBy = UUID.randomUUID())

    // Настроить mock MeterRegistry и RouteRepository

    // When
    val result = metricsService.getTopRoutes(MetricsSortBy.REQUESTS, 10, developer1Id).block()

    // Then
    assertThat(result).hasSize(1)
    assertThat(result[0].routeId).isEqualTo(route1.id.toString())
}

@Test
fun `getTopRoutes без ownerId возвращает все маршруты`() {
    // Given: маршруты созданные разными пользователями

    // When
    val result = metricsService.getTopRoutes(MetricsSortBy.REQUESTS, 10, null).block()

    // Then: все маршруты возвращаются
}
```

**Integration тест MetricsController:**
```kotlin
@Test
fun `developer видит только свои маршруты в top-routes`() {
    // Given: 2 маршрута — один создан текущим developer, другой — другим
    // JWT token для developer

    // When: GET /api/v1/metrics/top-routes

    // Then: возвращается только маршрут текущего developer
}

@Test
fun `admin видит все маршруты в top-routes`() {
    // Given: маршруты созданные разными пользователями
    // JWT token для admin

    // When: GET /api/v1/metrics/top-routes

    // Then: возвращаются все маршруты
}
```

### Project Structure Notes

**Модифицируемые файлы:**
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/MetricsController.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/MetricsService.kt`
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/service/MetricsServiceTest.kt`
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/MetricsControllerIntegrationTest.kt`

### Dependencies

- **Blocks:** Story 6.5 — AC6 (фильтрация TopRoutes для developer)
- **Requires:** JWT claims содержат `userId` и `role` (реализовано в Story 2.2)
- **Requires:** Route entity имеет поле `createdBy` (реализовано в Story 3.1)

### Git Context

**Последние коммиты:**
```
290a0a8 feat: implement Story 6.5 — Basic Metrics View in Admin UI
ab6bac8 feat: implement Story 6.4 — Prometheus & Grafana Setup
806acca feat: implement Story 6.3 — Metrics Summary API
b3157bb fix: code review fixes for Story 6.2 — add integration tests, improve logging
3dbbbd6 feat: implement Story 6.2 — Per-Route Metrics
```

**Паттерн коммита:**
```
feat: implement Story 6.5.1 — Role-Based Filtering for Metrics API
```

### References

- [Source: implementation-artifacts/6-5-basic-metrics-view-admin-ui.md] — AC6 требует эту функциональность
- [Source: implementation-artifacts/6-3-metrics-summary-api.md] — текущая реализация API
- [Source: implementation-artifacts/2-2-jwt-authentication-service.md] — JWT claims структура
- [Source: CLAUDE.md] — Coding conventions, reactive patterns

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

Не требуется — реализация прошла без критических проблем.

### Completion Notes List

1. **Task 1 (MetricsController):** Добавлена интеграция с `SecurityContextUtils.currentUser()` для получения текущего пользователя. Для роли DEVELOPER передаётся `ownerId = userId`, для ADMIN/SECURITY — `null` (без фильтрации).

2. **Task 2 (MetricsService):** Добавлен опциональный параметр `ownerId: UUID? = null` в метод `getTopRoutes()`. Если ownerId указан, маршруты фильтруются по `route.createdBy == ownerId` через reactive filter.

3. **Task 3 (Integration tests):** Добавлены 6 интеграционных тестов в `MetricsControllerIntegrationTest`:
   - AC1: developer видит только свои маршруты, не видит маршруты других
   - AC2: admin и security видят все маршруты без фильтрации
   - AC3: формат ответа сохранён, параметры `by` и `limit` работают

4. **Task 4 (Unit tests):** Добавлены 4 unit теста в `MetricsServiceTest`:
   - Фильтрация по ownerId работает корректно
   - Без ownerId возвращаются все маршруты
   - Пустой список при отсутствии маршрутов пользователя
   - Корректная сортировка после фильтрации

### Change Log

- 2026-02-20: Story создана из Course Correction workflow (code review Story 6.5)
- 2026-02-20: Story обогащена полным контекстом через Create Story workflow
- 2026-02-20: Реализованы все 4 задачи (AC1, AC2, AC3 выполнены)
- 2026-02-20: Все тесты gateway-admin проходят (добавлено 4 unit + 6 integration тестов для Story 6.5.1)
- 2026-02-20: Code Review — исправлены 3 MEDIUM issues (документация KDoc, защита SecurityContext, Change Log)

### Senior Developer Review (AI)

**Reviewer:** Claude Opus 4.5
**Date:** 2026-02-20
**Outcome:** ✅ APPROVED (после исправлений)

**AC Validation:**
- AC1 ✅ Developer фильтрация реализована (MetricsController:105-114, MetricsService:235-238)
- AC2 ✅ Admin/Security полный доступ (ownerId = null для не-DEVELOPER ролей)
- AC3 ✅ API контракт сохранён (формат и параметры без изменений)

**Issues Fixed:**
1. ✅ Неточное количество тестов в Change Log — исправлено
2. ✅ Неочевидное поведение limit после фильтрации — добавлена документация в KDoc
3. ✅ Отсутствовала защита при пустом SecurityContext — добавлен switchIfEmpty

**Notes:**
- 4 LOW issues не исправлены (не влияют на функциональность)
- /metrics/routes/{id} не фильтрует по owner — out of scope, рекомендуется для будущих stories

### File List

**Modified:**
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/MetricsController.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/MetricsService.kt`
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/service/MetricsServiceTest.kt`
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/MetricsControllerIntegrationTest.kt`
