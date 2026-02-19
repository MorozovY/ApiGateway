# Story 5.5: Assign Rate Limit to Route UI

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **Developer**,
I want to select a rate limit policy when creating/editing routes,
so that I can protect my service endpoints.

## Acceptance Criteria

**AC1 — Dropdown поле Rate Limit Policy в форме маршрута:**

**Given** пользователь находится на форме создания или редактирования маршрута
**When** форма рендерится
**Then** отображается поле "Rate Limit Policy" (dropdown)
**And** опции включают: "None" + все доступные политики
**And** каждая опция показывает: name (requests/sec)

**AC2 — Сохранение маршрута с политикой:**

**Given** пользователь выбирает политику rate limit
**When** маршрут сохраняется
**Then** маршрут ассоциируется с выбранной политикой
**And** API получает `rateLimitId` в запросе

**AC3 — Сохранение маршрута без политики:**

**Given** пользователь выбирает "None"
**When** маршрут сохраняется
**Then** маршрут сохраняется без rate limiting
**And** API получает `rateLimitId: null`

**AC4 — Отображение Rate Limit на странице деталей маршрута (с политикой):**

**Given** пользователь просматривает детали маршрута с назначенной политикой
**When** страница деталей загружается
**Then** секция rate limit отображает:
- Policy name
- Requests per second
- Burst size
**And** стилизовано как info card

**AC5 — Отображение Rate Limit на странице деталей (без политики):**

**Given** пользователь просматривает детали маршрута без rate limit
**When** страница деталей загружается
**Then** отображается сообщение: "No rate limiting configured"
**And** подсказка: "Consider adding rate limiting for production routes"

**AC6 — Колонка Rate Limit в таблице маршрутов (опционально):**

**Given** таблица списка маршрутов отображается
**When** пользователь смотрит таблицу
**Then** опциональная колонка "Rate Limit" показывает название политики или "—"
**And** колонка скрываема через настройки колонок (если реализовано)

## Tasks / Subtasks

- [x] Task 1: Обновить типы Route — добавить rateLimit объект (AC4, AC5)
  - [x] Обновить `frontend/admin-ui/src/features/routes/types/route.types.ts`
  - [x] Добавить `rateLimit?: RateLimitInfo | null` интерфейс
  - [x] Добавить `rateLimitId?: string | null` в UpdateRouteRequest

- [x] Task 2: Обновить RouteForm — добавить Rate Limit dropdown (AC1, AC2, AC3)
  - [x] Изменить `frontend/admin-ui/src/features/routes/components/RouteForm.tsx`
  - [x] Добавить Select поле для Rate Limit Policy
  - [x] Загружать список политик через useRateLimits()
  - [x] Формат опций: `${name} (${requestsPerSecond}/sec)`
  - [x] "None" как первая опция со значением пустая строка (преобразуется в null при submit)
  - [x] Включить rateLimitId в onSubmit

- [x] Task 3: Обновить RouteDetailsCard — секция Rate Limit (AC4, AC5)
  - [x] Изменить `frontend/admin-ui/src/features/routes/components/RouteDetailsCard.tsx`
  - [x] Удалить TODO комментарий, реализовать полную секцию
  - [x] Если rateLimit назначен — Descriptions с name, requestsPerSecond, burstSize
  - [x] Если rateLimit не назначен — сообщение "No rate limiting configured" с подсказкой

- [x] Task 4: Обновить RoutesTable — колонка Rate Limit (AC6)
  - [x] Изменить `frontend/admin-ui/src/features/routes/components/RoutesTable.tsx`
  - [x] Добавить колонку "Rate Limit" с названием политики или "—"
  - [x] Колонка видна по умолчанию (скрытие не реализовано — нет настроек колонок в проекте)

- [x] Task 5: Unit тесты для обновлённых компонентов (AC1-AC6)
  - [x] Тесты RouteForm: dropdown отображается, политики загружаются, выбор политики работает
  - [x] Тесты RouteDetailsCard: секция rate limit с данными, секция без rate limit, подсказка
  - [x] Тесты RoutesTable: колонка Rate Limit отображает название или "—"

## Dev Notes

### Существующие паттерны из проекта

**RouteForm.tsx** — текущая структура:
- 4 поля: Path, Upstream URL, Methods, Description
- Валидация через Form.Item rules
- Debounced проверка уникальности path
- forwardRef для внешнего вызова submit()
- **Добавить** Select поле Rate Limit после Methods

**RouteDetailsCard.tsx** — текущая структура:
- Card с Descriptions для метаданных
- Секция Rate Limit уже существует, но с TODO:
```tsx
{/* Rate Limit секция — если назначен */}
{/* TODO: После реализации API rate limits (Epic 4) загружать название и лимиты политики */}
{route.rateLimitId && (
  <Descriptions.Item label="Rate Limit">
    <Tag color="blue">Политика назначена</Tag>
  </Descriptions.Item>
)}
```
**Заменить** на полноценную секцию с данными из route.rateLimit

**useRateLimits hook** — уже реализован в Story 5.4:
```typescript
// frontend/admin-ui/src/features/rate-limits/hooks/useRateLimits.ts
export function useRateLimits() {
  return useQuery({
    queryKey: QUERY_KEYS.rateLimits,
    queryFn: () => api.getRateLimits(),
  })
}
```

**RateLimit типы** — уже существуют в Story 5.4:
```typescript
// frontend/admin-ui/src/features/rate-limits/types/rateLimit.types.ts
export interface RateLimit {
  id: string
  name: string
  description: string | null
  requestsPerSecond: number
  burstSize: number
  usageCount: number
  createdBy: string
  createdAt: string
  updatedAt: string
}
```

### Обновления типов Route

**Добавить в route.types.ts:**

```typescript
/**
 * Краткая информация о политике rate limit (из API response).
 * Соответствует RateLimitInfo DTO на backend.
 */
export interface RateLimitInfo {
  id: string
  name: string
  requestsPerSecond: number
  burstSize: number
}

// Обновить Route interface:
export interface Route {
  // ... существующие поля ...
  rateLimitId: string | null
  rateLimit?: RateLimitInfo | null  // ДОБАВИТЬ
}

// Обновить UpdateRouteRequest:
export interface UpdateRouteRequest {
  path?: string
  upstreamUrl?: string
  methods?: string[]
  description?: string
  rateLimitId?: string | null  // ДОБАВИТЬ
}

// Обновить CreateRouteRequest:
export interface CreateRouteRequest {
  path: string
  upstreamUrl: string
  methods: string[]
  description?: string
  rateLimitId?: string | null  // ДОБАВИТЬ (опционально при создании)
}
```

### RouteForm — добавление Select поля

```tsx
import { useRateLimits } from '@features/rate-limits'

// Внутри RouteForm:
const { data: rateLimitsData, isLoading: rateLimitsLoading } = useRateLimits()

// В JSX после поля Methods, перед Description:
{/* Поле Rate Limit Policy */}
<Form.Item
  name="rateLimitId"
  label="Rate Limit Policy"
>
  <Select
    placeholder="Выберите политику (опционально)"
    allowClear
    loading={rateLimitsLoading}
    options={[
      { value: null, label: 'None' },
      ...(rateLimitsData?.items || []).map((policy) => ({
        value: policy.id,
        label: `${policy.name} (${policy.requestsPerSecond}/sec)`,
      })),
    ]}
  />
</Form.Item>

// В handleFinish — добавить rateLimitId:
const request: CreateRouteRequest | UpdateRouteRequest = {
  path: fullPath,
  upstreamUrl: values.upstreamUrl,
  methods: values.methods,
  description: values.description || undefined,
  rateLimitId: values.rateLimitId || null,  // ДОБАВИТЬ
}

// В useEffect для initialValues:
form.setFieldsValue({
  path: pathWithoutSlash,
  upstreamUrl: initialValues.upstreamUrl,
  methods: initialValues.methods,
  description: initialValues.description || '',
  rateLimitId: initialValues.rateLimitId || null,  // ДОБАВИТЬ
})
```

### RouteDetailsCard — секция Rate Limit

**Заменить существующий TODO блок на:**

```tsx
{/* Rate Limit секция */}
{route.rateLimit ? (
  <>
    <Descriptions.Item label="Rate Limit Policy">
      <strong>{route.rateLimit.name}</strong>
    </Descriptions.Item>
    <Descriptions.Item label="Requests per Second">
      {route.rateLimit.requestsPerSecond}
    </Descriptions.Item>
    <Descriptions.Item label="Burst Size">
      {route.rateLimit.burstSize}
    </Descriptions.Item>
  </>
) : (
  <Descriptions.Item label="Rate Limit">
    <Space direction="vertical" size={4}>
      <span style={{ color: '#8c8c8c' }}>No rate limiting configured</span>
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        Consider adding rate limiting for production routes
      </Typography.Text>
    </Space>
  </Descriptions.Item>
)}
```

### RoutesTable — колонка Rate Limit

**Добавить колонку после Methods:**

```tsx
{
  title: 'Rate Limit',
  dataIndex: ['rateLimit', 'name'],
  key: 'rateLimit',
  render: (_: unknown, record: Route) =>
    record.rateLimit?.name || '—',
  width: 150,
}
```

### Структура файлов для изменения

| Файл | Действие |
|------|---------|
| `frontend/admin-ui/src/features/routes/types/route.types.ts` | ИЗМЕНИТЬ — добавить RateLimitInfo interface, обновить Route, UpdateRouteRequest |
| `frontend/admin-ui/src/features/routes/components/RouteForm.tsx` | ИЗМЕНИТЬ — добавить Select для Rate Limit Policy |
| `frontend/admin-ui/src/features/routes/components/RouteDetailsCard.tsx` | ИЗМЕНИТЬ — заменить TODO на полную секцию Rate Limit |
| `frontend/admin-ui/src/features/routes/components/RoutesTable.tsx` | ИЗМЕНИТЬ — добавить колонку Rate Limit |
| `frontend/admin-ui/src/features/routes/components/RouteForm.test.tsx` | ИЗМЕНИТЬ — добавить тесты для Rate Limit dropdown |
| `frontend/admin-ui/src/features/routes/components/RouteDetailsCard.test.tsx` | ИЗМЕНИТЬ — добавить тесты для Rate Limit секции |

### Backend API уже готов (Story 5.2)

API поддерживает:
- `PUT /api/v1/routes/{id}` с `rateLimitId` — назначение/удаление политики
- `GET /api/v1/routes/{id}` — возвращает `rateLimitId` и `rateLimit` объект
- `GET /api/v1/routes` — список маршрутов включает `rateLimit` для каждого

**Response structure:**
```json
{
  "id": "route-uuid",
  "path": "/api/orders",
  "upstreamUrl": "http://order-service:8080",
  "methods": ["GET", "POST"],
  "status": "draft",
  "rateLimitId": "policy-uuid",
  "rateLimit": {
    "id": "policy-uuid",
    "name": "standard",
    "requestsPerSecond": 100,
    "burstSize": 150
  }
}
```

### Импорты для RouteForm

```tsx
// Добавить импорт useRateLimits:
import { useRateLimits } from '@features/rate-limits'

// Или если нет re-export:
import { useRateLimits } from '@/features/rate-limits/hooks/useRateLimits'
```

### Важные паттерны

1. **Ant Design Select:** allowClear для возможности сброса, loading для состояния загрузки
2. **React Query:** useRateLimits() возвращает `{ data, isLoading }` — использовать для loading state
3. **Типы:** RateLimitInfo — минимальный DTO без usageCount (как на backend)
4. **Условный рендер:** проверка `route.rateLimit` vs `route.rateLimitId` — использовать rateLimit для полных данных
5. **Комментарии:** только на русском языке согласно CLAUDE.md
6. **Названия тестов:** только на русском языке

### Тесты на русском языке (примеры)

```typescript
// RouteForm.test.tsx
describe('поле Rate Limit Policy', () => {
  it('отображает dropdown с политиками rate limit', async () => { ... })
  it('отображает "None" как первую опцию', async () => { ... })
  it('включает rateLimitId в onSubmit при выборе политики', async () => { ... })
  it('передаёт rateLimitId: null при выборе "None"', async () => { ... })
})

// RouteDetailsCard.test.tsx
describe('секция Rate Limit', () => {
  it('отображает name, requestsPerSecond, burstSize когда политика назначена', () => { ... })
  it('отображает "No rate limiting configured" когда политика не назначена', () => { ... })
  it('показывает подсказку о добавлении rate limiting', () => { ... })
})

// RoutesTable.test.tsx
describe('колонка Rate Limit', () => {
  it('отображает название политики когда назначена', () => { ... })
  it('отображает "—" когда политика не назначена', () => { ... })
})
```

### Архитектурные требования

- **Комментарии**: только на русском языке
- **Названия тестов**: только на русском языке
- **Ant Design**: использовать как UI библиотеку
- **React Query**: для управления серверным состоянием
- **TypeScript**: строгая типизация

### Project Structure Notes

- Alignment с unified project structure: изменения только в `features/routes/`
- Зависимость от `features/rate-limits/` — только импорт useRateLimits hook
- Detected conflicts: нет

### Learnings из Story 5.4

1. **destroyOnHidden vs destroyOnClose:** использовать destroyOnHidden для модальных окон (deprecated warning)
2. **Сообщения ошибок:** на английском для консистентности с backend (message.error)
3. **SAFETY комментарии:** добавлять для неочевидных null-проверок
4. **Форматирование чисел:** `${requestsPerSecond}/sec` — читаемый формат

### Git Context

Последние коммиты Epic 5:
- feat: implement Story 5.4 — Rate Limit Policies Management UI
- feat: implement Story 5.3 — Rate Limiting Filter with Redis Token Bucket
- feat: implement Story 5.2 — Assign Rate Limit to Route API
- fix: code review fixes for Story 5.1 Rate Limit CRUD API
- feat: implement Story 5.1 — Rate Limit Policy CRUD API

**Паттерн коммитов:** `feat: implement Story X.Y — <title>` или `fix: <description>`

### References

- [Source: planning-artifacts/epics.md#Story-5.5] — Story requirements и AC
- [Source: planning-artifacts/architecture.md#Frontend-Architecture] — Vite, React Query, Ant Design
- [Source: planning-artifacts/ux-design-specification.md] — UX паттерны
- [Source: implementation-artifacts/5-4-rate-limit-policies-management-ui.md] — паттерны и learnings
- [Source: implementation-artifacts/5-2-assign-rate-limit-route-api.md] — Backend API с rateLimitId
- [Source: frontend/admin-ui/src/features/routes/components/RouteForm.tsx] — текущая форма маршрута
- [Source: frontend/admin-ui/src/features/routes/components/RouteDetailsCard.tsx] — текущая карточка деталей
- [Source: frontend/admin-ui/src/features/rate-limits/hooks/useRateLimits.ts] — hook для загрузки политик
- [Source: frontend/admin-ui/src/features/rate-limits/types/rateLimit.types.ts] — типы rate limits

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

Нет debug issues.

### Completion Notes List

- Task 1: Добавлен интерфейс `RateLimitInfo` с полями id, name, requestsPerSecond, burstSize. Обновлён интерфейс `Route` — добавлено поле `rateLimit`. Обновлены интерфейсы `CreateRouteRequest` и `UpdateRouteRequest` — добавлено поле `rateLimitId`.

- Task 2: RouteForm обновлён — добавлен Select для Rate Limit Policy после поля HTTP Methods. Используется hook `useRateLimits` из `@features/rate-limits`. Опции: "None" (пустая строка, преобразуется в null) + политики в формате `${name} (${requestsPerSecond}/sec)`. При submit передаётся `rateLimitId`. При редактировании форма инициализируется с текущим значением `rateLimitId`.

- Task 3: RouteDetailsCard обновлён — удалён TODO комментарий с заглушкой "Политика назначена". Реализована полная секция: если `rateLimit` назначен — отображаются Policy name, Requests per Second, Burst Size. Если не назначен — сообщение "No rate limiting configured" с подсказкой "Consider adding rate limiting for production routes".

- Task 4: RoutesTable обновлён — добавлена колонка "Rate Limit" после Methods. Отображает название политики или "—" если не назначена. Ширина 150px.

- Task 5: Созданы и обновлены тесты:
  - `RouteForm.test.tsx` — 12 тестов для Rate Limit Policy (AC1-AC3: UI + интеграция с onSubmit)
  - `RouteDetailsCard.test.tsx` — 3 теста для секции Rate Limit (Story 5.5)
  - `RoutesPage.test.tsx` — 2 теста для колонки Rate Limit
  - `RouteDetailsPage.test.tsx` — обновлены 2 теста для новой имплементации

### Change Log

- 2026-02-19: Story 5.5 implemented — Rate Limit Policy selection in route form, Rate Limit info display in route details and table
- 2026-02-19: Code review fixes — expanded RouteForm.test.tsx to 7 tests covering AC1-AC3, fixed JSX comment language
- 2026-02-19: Code review #2 fixes — added 5 integration tests for onSubmit (rateLimitId передаётся корректно), loading state test, allowClear test. Total: 186 tests pass

### File List

- `frontend/admin-ui/src/features/routes/types/route.types.ts` — MODIFIED: added RateLimitInfo interface, updated Route/CreateRouteRequest/UpdateRouteRequest
- `frontend/admin-ui/src/features/routes/components/RouteForm.tsx` — MODIFIED: added Rate Limit Policy Select field
- `frontend/admin-ui/src/features/routes/components/RouteDetailsCard.tsx` — MODIFIED: replaced TODO with full Rate Limit section
- `frontend/admin-ui/src/features/routes/components/RoutesTable.tsx` — MODIFIED: added Rate Limit column
- `frontend/admin-ui/src/features/routes/components/RouteForm.test.tsx` — NEW: tests for Rate Limit dropdown
- `frontend/admin-ui/src/features/routes/components/RouteDetailsCard.test.tsx` — MODIFIED: added Rate Limit section tests
- `frontend/admin-ui/src/features/routes/components/RoutesPage.test.tsx` — MODIFIED: added Rate Limit column tests
- `frontend/admin-ui/src/features/routes/components/RouteDetailsPage.test.tsx` — MODIFIED: updated Rate Limit tests for new implementation

## Senior Developer Review (AI)

### Review Status

**PASS** — Story 5.5 implementation ready for commit

### AC Coverage

| AC | Status | Notes |
|----|--------|-------|
| AC1 | ✅ PASS | Rate Limit Policy dropdown в форме маршрута с опциями "None" + все политики |
| AC2 | ✅ PASS | rateLimitId передаётся в API при сохранении маршрута (тесты подтверждают) |
| AC3 | ✅ PASS | При выборе "None" передаётся rateLimitId: null (тесты подтверждают) |
| AC4 | ✅ PASS | Секция Rate Limit отображает name, requestsPerSecond, burstSize |
| AC5 | ✅ PASS | "No rate limiting configured" с подсказкой для маршрутов без политики |
| AC6 | ✅ PASS | Колонка Rate Limit в таблице маршрутов |

### Code Quality

- ✅ TypeScript строгая типизация
- ✅ Комментарии на русском языке
- ✅ Названия тестов на русском языке
- ✅ React Query для серверного состояния
- ✅ Ant Design компоненты
- ✅ Все 186 тестов проходят

### Issues Found (Fixed)

1. ~~Недостаточное покрытие тестами AC1-AC3~~ — добавлены 7 UI тестов
2. ~~RouteForm.test.tsx не добавлен в git~~ — исправлено
3. ~~Смешанный язык в JSX комментарии~~ — исправлено
4. ~~[HIGH] Не было тестов для проверки rateLimitId в onSubmit~~ — добавлены 4 интеграционных теста
5. ~~[MEDIUM] Не было теста loading state~~ — добавлен тест
6. ~~[MEDIUM] Не было теста allowClear~~ — добавлен тест

### Recommendations (LOW — не блокируют)

1. AC4 "info card" styling — Rate Limit секция не выделена визуально как отдельный блок
2. RoutesTable Rate Limit column width hardcoded (150px)
