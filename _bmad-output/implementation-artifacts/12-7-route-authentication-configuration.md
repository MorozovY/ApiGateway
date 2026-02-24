# Story 12.7: Route Authentication Configuration

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **Developer**,
I want to configure authentication requirements per route,
So that I can have both public and protected endpoints (FR37, FR38, FR39).

## Feature Context

**Source:** Epic 12 — Keycloak Integration & Multi-tenant Metrics (Phase 2 PRD 2026-02-23)
**Business Value:** Позволяет Developer'ам гибко настраивать аутентификацию для каждого маршрута. Публичные endpoints (health checks, public APIs) могут работать без JWT, а защищённые endpoints требуют валидный токен. Дополнительно можно ограничить доступ к маршруту определённым consumers (whitelist).

**Blocking Dependencies:**
- Story 12.1 (Keycloak Setup & Configuration) — DONE ✅
- Story 12.4 (Gateway Core JWT Authentication Filter) — DONE ✅ — фильтр проверяет auth_required/allowed_consumers
- Story 12.5 (Gateway Core Consumer Identity Filter) — DONE ✅ — определяет consumer_id

**Blocked By This Story:**
- Story 12.8 (Per-consumer Rate Limits) — нужно UI для конфигурации маршрутов
- Story 12.10 (E2E Tests) — тестирование public/protected routes

## Acceptance Criteria

### AC1: Route Create/Edit Form — Authentication Toggle
**Given** route create/edit form
**When** form renders
**Then** "Authentication Required" toggle is displayed (default: ON)
**And** "Allowed Consumers" multi-select is displayed (optional)

### AC2: Database Migration (DONE — V10 already exists)
**Given** migration V10__add_route_auth_fields.sql
**When** executed
**Then** columns are added to routes table:
- `auth_required` (BOOLEAN, NOT NULL, DEFAULT true)
- `allowed_consumers` (TEXT[], nullable)

**NOTE:** Миграция V10 уже создана в Story 12.4. Поля `authRequired` и `allowedConsumers` уже присутствуют в Route entity (gateway-common). Эта задача полностью выполнена.

### AC3: Routes List — Protected/Public Badge
**Given** route with `auth_required = true`
**When** displayed in routes list
**Then** "Protected" badge is shown

**Given** route with `auth_required = false`
**When** displayed in routes list
**Then** "Public" badge is shown

### AC4: Route Details API — Auth Fields
**Given** route details API
**When** GET `/api/v1/routes/{id}`
**Then** response includes `authRequired` and `allowedConsumers` fields

### AC5: Route Create/Update API — Auth Fields
**Given** route creation/update request
**When** `authRequired` and/or `allowedConsumers` are included in request body
**Then** route is saved with these authentication settings
**And** changes are reflected in response

## Tasks / Subtasks

- [x] Task 0: Pre-flight Checklist (PA-09)
  - [x] 0.1 Проверить что gateway-admin запускается: `./gradlew :gateway-admin:bootRun`
  - [x] 0.2 Проверить что миграция V10 применяется корректно
  - [x] 0.3 Проверить что Route entity содержит authRequired и allowedConsumers поля
  - [x] 0.4 Проверить что все тесты проходят: `./gradlew :gateway-admin:test`

- [x] Task 1: Backend — Extend CreateRouteRequest/UpdateRouteRequest (AC: #5)
  - [x] 1.1 Добавить `authRequired: Boolean = true` в CreateRouteRequest
  - [x] 1.2 Добавить `allowedConsumers: List<String>? = null` в CreateRouteRequest
  - [x] 1.3 Добавить те же поля в UpdateRouteRequest
  - [x] 1.4 Обновить RouteService.create() — передавать authRequired и allowedConsumers в Route constructor
  - [x] 1.5 Обновить RouteService.update() — обновлять authRequired и allowedConsumers

- [x] Task 2: Backend — Extend RouteResponse (AC: #4)
  - [x] 2.1 Добавить `authRequired: Boolean` в RouteResponse
  - [x] 2.2 Добавить `allowedConsumers: List<String>?` в RouteResponse
  - [x] 2.3 Обновить RouteResponse.from() — маппить поля из Route entity
  - [x] 2.4 Обновить RouteDetailResponse (если отличается от RouteResponse)
  - [x] 2.5 Обновить RouteListResponse (добавить поля для badge в таблице)

- [x] Task 3: Backend — Unit Tests (AC: #4, #5)
  - [x] 3.1 Тест: создание маршрута с authRequired=true (default)
  - [x] 3.2 Тест: создание маршрута с authRequired=false (public route)
  - [x] 3.3 Тест: создание маршрута с allowedConsumers whitelist
  - [x] 3.4 Тест: обновление authRequired/allowedConsumers
  - [x] 3.5 Тест: GET /routes/{id} возвращает authRequired и allowedConsumers

- [x] Task 4: Frontend — Extend Route Types (AC: #1)
  - [x] 4.1 Добавить `authRequired: boolean` в Route interface
  - [x] 4.2 Добавить `allowedConsumers: string[] | null` в Route interface
  - [x] 4.3 Добавить те же поля в CreateRouteRequest и UpdateRouteRequest

- [x] Task 5: Frontend — RouteForm Extensions (AC: #1)
  - [x] 5.1 Добавить Switch "Authentication Required" (default: checked)
  - [x] 5.2 Добавить Select multi-mode "Allowed Consumers" (tags mode, optional)
  - [x] 5.3 Добавить подсказки: "Public route — no JWT required", "Protected route — JWT required"
  - [x] 5.4 Добавить authRequired и allowedConsumers в form submit handler
  - [x] 5.5 Добавить initialValues для authRequired и allowedConsumers в edit mode

- [x] Task 6: Frontend — RoutesTable Badge (AC: #3)
  - [x] 6.1 Добавить колонку "Auth" или badge в существующую колонку Status
  - [x] 6.2 "Protected" badge (зелёный lock icon или tag)
  - [x] 6.3 "Public" badge (серый unlock icon или tag)

- [x] Task 7: Frontend — Unit Tests (AC: #1, #3)
  - [x] 7.1 Тест: RouteForm рендерит Authentication toggle
  - [x] 7.2 Тест: RouteForm включает authRequired в submit
  - [x] 7.3 Тест: RoutesTable показывает Protected/Public badge
  - [x] 7.4 Тест: RouteForm в edit mode загружает authRequired/allowedConsumers

- [x] Task 8: Documentation Update (AC: #2)
  - [x] 8.1 Обновить API docs (автоматически через KDoc)
  - [x] 8.2 Добавить KDoc комментарии в DTOs для Swagger
  - [x] 8.3 Документировать default значения: authRequired=true, allowedConsumers=null

- [ ] Task 9: Manual Verification (User)
  - [ ] 9.1 Создать public route (authRequired=false) через UI
  - [ ] 9.2 Проверить что badge "Public" отображается в таблице
  - [ ] 9.3 Создать protected route с whitelist (allowedConsumers=["company-a"])
  - [ ] 9.4 Проверить GET /routes/{id} возвращает все поля
  - [ ] 9.5 Отредактировать route — поменять authRequired — сохранить

## API Dependencies Checklist

**Backend API endpoints, используемые в этой story:**

| Endpoint | Method | Параметры | Статус |
|----------|--------|-----------|--------|
| `/api/v1/routes` | POST | `authRequired`, `allowedConsumers` | ✅ Реализовано |
| `/api/v1/routes/{id}` | PUT | `authRequired`, `allowedConsumers` | ✅ Реализовано |
| `/api/v1/routes/{id}` | GET | — | ✅ Расширен response |
| `/api/v1/routes` | GET | — | ✅ Расширен response |

**Проверки выполнены:**

- [x] Database migration V10 существует и применяется
- [x] Route entity содержит поля authRequired и allowedConsumers
- [x] DTOs (CreateRouteRequest, UpdateRouteRequest, RouteResponse) расширены
- [x] Frontend types обновлены

## Dev Notes

### Database Schema (Already Done)

Миграция V10__add_route_auth_fields.sql уже создана:

```sql
ALTER TABLE routes ADD COLUMN auth_required BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE routes ADD COLUMN allowed_consumers TEXT[] DEFAULT NULL;
```

### Route Entity (Already Done)

Поля уже добавлены в `gateway-common/model/Route.kt`:

```kotlin
@Column("auth_required")
val authRequired: Boolean = true,

@Column("allowed_consumers")
val allowedConsumers: List<String>? = null
```

### Backend Changes Required

**CreateRouteRequest.kt:**
```kotlin
data class CreateRouteRequest(
    // ... existing fields ...

    /** Требуется ли JWT аутентификация (default: true = protected) */
    val authRequired: Boolean = true,

    /** Whitelist consumer IDs (null = все разрешены) */
    val allowedConsumers: List<String>? = null
)
```

**RouteResponse.kt:**
```kotlin
data class RouteResponse(
    // ... existing fields ...

    val authRequired: Boolean,
    val allowedConsumers: List<String>?
) {
    companion object {
        fun from(route: Route, ...): RouteResponse {
            return RouteResponse(
                // ... existing mappings ...
                authRequired = route.authRequired,
                allowedConsumers = route.allowedConsumers
            )
        }
    }
}
```

### Frontend Changes Required

**route.types.ts:**
```typescript
export interface Route {
  // ... existing fields ...

  /** Требуется ли JWT аутентификация для маршрута */
  authRequired: boolean
  /** Whitelist consumer IDs (null = все разрешены) */
  allowedConsumers: string[] | null
}

export interface CreateRouteRequest {
  // ... existing fields ...
  authRequired?: boolean
  allowedConsumers?: string[] | null
}
```

**RouteForm.tsx additions:**
```tsx
// Добавить после Rate Limit Select

{/* Authentication Required Toggle */}
<Form.Item
  name="authRequired"
  label="Authentication Required"
  valuePropName="checked"
  initialValue={true}
  tooltip="Если включено, маршрут требует валидный JWT токен"
>
  <Switch
    checkedChildren="Protected"
    unCheckedChildren="Public"
  />
</Form.Item>

{/* Allowed Consumers Multi-select (опционально) */}
<Form.Item
  name="allowedConsumers"
  label="Allowed Consumers"
  tooltip="Оставьте пустым для доступа всем. Укажите client_id для whitelist."
>
  <Select
    mode="tags"
    placeholder="Введите consumer IDs (опционально)"
    tokenSeparators={[',', ' ']}
    allowClear
  />
</Form.Item>
```

**RoutesTable.tsx — badge column:**
```tsx
{
  title: 'Auth',
  dataIndex: 'authRequired',
  key: 'authRequired',
  width: 100,
  render: (authRequired: boolean) => (
    <Tag color={authRequired ? 'green' : 'default'}>
      {authRequired ? (
        <>
          <LockOutlined /> Protected
        </>
      ) : (
        <>
          <UnlockOutlined /> Public
        </>
      )}
    </Tag>
  ),
}
```

### Filter Chain Context

JwtAuthenticationFilter (Story 12.4) уже использует route.authRequired и route.allowedConsumers:
- Если `authRequired = false` → пропускает без JWT
- Если `allowedConsumers != null` → проверяет что consumer в whitelist

### PA-05: DTO Field Checklist

При добавлении authRequired/allowedConsumers в DTOs проверить:
- [ ] RouteService.create() — передаёт поля в Route constructor
- [ ] RouteService.update() — обновляет поля
- [ ] RouteResponse.from() — маппит поля из Route
- [ ] RouteDetailResponse — если отличается, тоже обновить
- [ ] RouteListResponse — для отображения badge в таблице

### File Structure

```
backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/
├── dto/
│   ├── CreateRouteRequest.kt      # МОДИФИЦИРОВАТЬ
│   ├── UpdateRouteRequest.kt      # МОДИФИЦИРОВАТЬ
│   ├── RouteResponse.kt           # МОДИФИЦИРОВАТЬ
│   ├── RouteDetailResponse.kt     # МОДИФИЦИРОВАТЬ (если нужно)
│   └── RouteListResponse.kt       # МОДИФИЦИРОВАТЬ (если нужно)
├── service/
│   └── RouteService.kt            # МОДИФИЦИРОВАТЬ (create/update)

backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/
├── controller/
│   └── RouteControllerTest.kt     # МОДИФИЦИРОВАТЬ (новые тесты)
├── service/
│   └── RouteServiceTest.kt        # МОДИФИЦИРОВАТЬ (новые тесты)

frontend/admin-ui/src/features/routes/
├── types/
│   └── route.types.ts             # МОДИФИЦИРОВАТЬ
├── components/
│   ├── RouteForm.tsx              # МОДИФИЦИРОВАТЬ
│   ├── RouteForm.test.tsx         # МОДИФИЦИРОВАТЬ
│   ├── RoutesTable.tsx            # МОДИФИЦИРОВАТЬ
│   └── RoutesTable.test.tsx       # МОДИФИЦИРОВАТЬ (если существует)
```

### Testing Strategy

1. **Backend Unit Tests:**
   - RouteControllerTest — create/update с auth fields
   - RouteServiceTest — business logic для auth fields

2. **Frontend Unit Tests:**
   - RouteForm — render switch, submit includes auth fields
   - RoutesTable — render Protected/Public badge

3. **Manual Testing:**
   - Создать public route → проверить badge
   - Создать protected route с whitelist → проверить API response
   - Edit route → изменить auth settings → сохранить

### Critical Constraints

1. **Default: authRequired = true** — новые маршруты защищены по умолчанию
2. **allowedConsumers = null** означает "все consumers разрешены"
3. **Не ломать существующие маршруты** — все текущие маршруты получат authRequired=true (из default в миграции)
4. **Валидация allowedConsumers** — если указан, все элементы должны быть непустыми строками

### Previous Story Intelligence

Из Story 12.4-12.6:
- Route entity в gateway-common уже содержит поля authRequired и allowedConsumers
- JwtAuthenticationFilter использует route.authRequired для определения нужна ли проверка JWT
- ConsumerIdentityFilter определяет consumer_id из JWT azp claim
- Миграция V10 уже применена

### References

- [Source: architecture.md#Route Authentication Configuration]
- [Source: architecture.md#Route API Changes]
- [Source: prd.md#FR37-FR41]
- [Source: epics.md#Story 12.7]
- [Source: gateway-common/model/Route.kt] — Route entity с authRequired/allowedConsumers
- [Source: 12-4-gateway-core-jwt-authentication-filter.md] — JwtAuthenticationFilter implementation
- [Source: backend/gateway-admin/src/main/resources/db/migration/V10__add_route_auth_fields.sql] — Migration

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

N/A

### Completion Notes List

1. Все backend изменения выполнены: CreateRouteRequest, UpdateRouteRequest, RouteResponse, RouteDetailResponse, RouteWithCreator, RouteRepositoryCustomImpl, RouteService
2. Все frontend изменения выполнены: route.types.ts, RouteForm.tsx, RoutesTable.tsx
3. Backend тесты добавлены в RouteControllerIntegrationTest (7 тестов)
4. Frontend тесты добавлены в RouteForm.test.tsx (6 тестов) и RoutesTable.test.tsx (3 теста)
5. Все 640 frontend тестов и все backend тесты проходят

### File List

**Backend модифицированные файлы:**
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/CreateRouteRequest.kt` — добавлены authRequired, allowedConsumers
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/UpdateRouteRequest.kt` — добавлены authRequired, allowedConsumers с *Provided флагами
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/RouteResponse.kt` — добавлены authRequired, allowedConsumers
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/RouteDetailResponse.kt` — добавлены authRequired, allowedConsumers
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/RouteRepositoryCustomImpl.kt` — обновлены SQL запросы и mappers
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/RouteService.kt` — обновлены create() и update()
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/RouteControllerIntegrationTest.kt` — добавлены тесты Story 12.7

**Frontend модифицированные файлы:**
- `frontend/admin-ui/src/features/routes/types/route.types.ts` — добавлены authRequired, allowedConsumers
- `frontend/admin-ui/src/features/routes/components/RouteForm.tsx` — добавлены Switch и Select для auth настроек
- `frontend/admin-ui/src/features/routes/components/RoutesTable.tsx` — добавлена колонка Auth с badge
- `frontend/admin-ui/src/features/routes/components/RouteForm.test.tsx` — добавлены тесты auth полей
- `frontend/admin-ui/src/features/routes/components/RoutesTable.test.tsx` — новый файл с тестами badge
