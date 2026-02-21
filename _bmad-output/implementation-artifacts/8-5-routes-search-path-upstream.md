# Story 8.5: Поиск Routes по Path и Upstream URL

Status: done

## Story

As a **User**,
I want search on Routes page to filter by both Path and Upstream URL,
so that I can find routes by any criteria.

## Acceptance Criteria

**AC1 — Поиск фильтрует по Path OR Upstream URL:**

**Given** пользователь находится на странице `/routes`
**When** пользователь вводит "order" в поле поиска
**Then** таблица показывает маршруты где Path OR Upstream URL содержит "order"
**And** поиск регистронезависимый (case-insensitive)

**AC2 — Backend API фильтрует по path OR upstream_url:**

**Given** GET `/api/v1/routes` с параметром `search`
**When** API обрабатывает запрос
**Then** SQL фильтрует: `path ILIKE '%search%' OR upstream_url ILIKE '%search%'`
**And** поиск регистронезависимый

**AC3 — Placeholder в поле поиска обновлён:**

**Given** пользователь видит поле поиска на странице `/routes`
**When** поле пустое
**Then** placeholder показывает "Поиск по path, upstream..."

## Tasks / Subtasks

- [x] Task 1: Backend — обновить SQL запрос search (AC2)
  - [x] Subtask 1.1: Изменить buildWhereClause() в RouteRepositoryCustomImpl.kt
  - [x] Subtask 1.2: Обновить документацию в RouteFilterRequest.kt

- [x] Task 2: Frontend — обновить placeholder (AC3)
  - [x] Subtask 2.1: Изменить placeholder в RoutesTable.tsx

- [x] Task 3: Тесты
  - [x] Subtask 3.1: Добавить integration тест — search по upstream URL (3 теста добавлены)
  - [x] Subtask 3.2: Обновить существующие тесты (placeholder обновлён в 3 местах)
  - [x] Subtask 3.3: Запустить все тесты ✅ PASSED

## API Dependencies Checklist

**Backend API endpoints, используемые в этой story:**

| Endpoint | Method | Параметры | Статус |
|----------|--------|-----------|--------|
| `/api/v1/routes` | GET | `search`, `status`, `upstream`, `offset`, `limit` | ✅ Существует, требует изменение search логики |

**Проверки перед началом разработки:**

- [x] API endpoint `/api/v1/routes` существует
- [x] Параметр `search` уже поддерживается (фильтрует по path OR description)
- [ ] Параметр `search` должен фильтровать по path OR upstream_url → **Требуется изменение**

## Dev Notes

### Текущее состояние кода

**Backend (RouteRepositoryCustomImpl.kt:120-124):**
```kotlin
// Текущая реализация — ищет по path OR description
search?.let {
    val escapedSearch = escapeForIlike(it)
    sql.append(" AND (path ILIKE :search ESCAPE '\\' OR description ILIKE :search ESCAPE '\\')")
    params["search"] = "%$escapedSearch%"
}
```

**Проблема:** По AC нужно искать по `path OR upstream_url`, а не `path OR description`.

**Frontend (RoutesTable.tsx:353-360):**
```tsx
<Input.Search
  placeholder="Поиск по path..."
  allowClear
  value={searchInput}
  onChange={(e) => handleSearchInputChange(e.target.value)}
  onSearch={handleSearchSubmit}
  style={{ width: 250 }}
  prefix={<SearchOutlined />}
/>
```

**Проблема:** Placeholder показывает только "path", но после изменения будет искать и по upstream.

### Решение

**1. Backend (RouteRepositoryCustomImpl.kt) — изменить SQL:**

```kotlin
// Было (line 122):
sql.append(" AND (path ILIKE :search ESCAPE '\\' OR description ILIKE :search ESCAPE '\\')")

// Стало:
sql.append(" AND (path ILIKE :search ESCAPE '\\' OR upstream_url ILIKE :search ESCAPE '\\')")
```

**2. Backend (RouteFilterRequest.kt) — обновить документацию:**

```kotlin
// Было (line 11):
// * - search: текстовый поиск по path и description (case-insensitive)

// Стало:
// * - search: текстовый поиск по path и upstream URL (case-insensitive)
```

**3. Frontend (RoutesTable.tsx) — обновить placeholder:**

```tsx
// Было:
placeholder="Поиск по path..."

// Стало:
placeholder="Поиск по path, upstream..."
```

### Важно: НЕ конфликтует с upstream параметром

В API уже есть отдельный параметр `upstream` для фильтрации по upstream URL (добавлен в Story 7.4).
Параметр `search` — это универсальный поиск для быстрого поиска по нескольким полям.
Эти параметры могут использоваться вместе:
- `search=order` — ищет "order" в path OR upstream_url
- `upstream=payment` — дополнительно фильтрует по upstream_url содержащему "payment"

### Project Structure Notes

| Файл | Путь | Изменение |
|------|------|-----------|
| RouteRepositoryCustomImpl.kt | `backend/gateway-admin/src/main/kotlin/.../repository/` | Изменить SQL в buildWhereClause() |
| RouteFilterRequest.kt | `backend/gateway-admin/src/main/kotlin/.../dto/` | Обновить KDoc комментарий |
| RoutesTable.tsx | `frontend/admin-ui/src/features/routes/components/` | Изменить placeholder |

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 8.5]
- [Source: backend/gateway-admin/src/main/kotlin/.../repository/RouteRepositoryCustomImpl.kt:120-124] — текущий search SQL
- [Source: backend/gateway-admin/src/main/kotlin/.../dto/RouteFilterRequest.kt:11] — документация search
- [Source: frontend/admin-ui/src/features/routes/components/RoutesTable.tsx:353-360] — поле поиска
- [Source: _bmad-output/implementation-artifacts/8-4-routes-author-rate-limit-display.md] — предыдущая story с паттернами

### Тестовые команды

```bash
# Backend integration тесты
./gradlew :gateway-admin:test --tests "*RouteController*"

# Frontend unit тесты
cd frontend/admin-ui
npm run test:run -- RoutesTable

# Все тесты
./gradlew test
cd frontend/admin-ui && npm run test:run
```

### Связанные stories

- Story 3.2 (Route List API with Filtering & Search) — базовая реализация search
- Story 7.4 (Routes by Upstream Filter) — добавлен отдельный параметр `upstream`
- Story 8.4 (Routes Author Rate Limit Display) — предыдущая story в Epic 8

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

N/A

### Completion Notes List

- ✅ Backend: SQL запрос search изменён с `path OR description` на `path OR upstream_url` (AC2)
- ✅ Backend: KDoc комментарий в RouteFilterRequest.kt обновлён
- ✅ Frontend: Placeholder обновлён на "Поиск по path, upstream..." (AC3)
- ✅ Backend тесты: Добавлено 3 теста в RouteControllerIntegrationTest.Story8_5_SearchByPathAndUpstream
- ✅ Frontend тесты: Placeholder обновлён в 3 местах RoutesPage.test.tsx
- ✅ Все тесты проходят: RouteController (backend), RoutesPage (frontend 26 тестов)

### File List

**Modified:**
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/repository/RouteRepositoryCustomImpl.kt`
- `backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/dto/RouteFilterRequest.kt`
- `backend/gateway-admin/src/test/kotlin/com/company/gateway/admin/integration/RouteControllerIntegrationTest.kt`
- `frontend/admin-ui/src/features/routes/components/RoutesTable.tsx`
- `frontend/admin-ui/src/features/routes/components/RoutesPage.test.tsx`
- `_bmad-output/implementation-artifacts/sprint-status.yaml`

## Change Log

- 2026-02-21: Story 8.5 implemented — search parameter now filters by path OR upstream_url instead of path OR description
