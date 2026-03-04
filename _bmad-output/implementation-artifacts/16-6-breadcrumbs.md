# Story 16.6: Breadcrumbs навигация

Status: done

## Story

As a **User**,
I want to see breadcrumbs on detail/edit pages,
so that I can understand my location and navigate back easily.

## Acceptance Criteria

### AC1: Страница редактирования маршрута
**Given** страница редактирования маршрута `/routes/:id/edit`
**When** пользователь видит страницу
**Then** отображаются breadcrumbs: "Маршруты > {route.path} > Редактирование"
**And** "Маршруты" кликабелен и ведёт на `/routes`
**And** "{route.path}" кликабелен и ведёт на `/routes/:id`

### AC2: Страница просмотра маршрута
**Given** страница просмотра маршрута `/routes/:id`
**When** пользователь видит страницу
**Then** отображаются breadcrumbs: "Маршруты > {route.path}"
**And** "Маршруты" кликабелен

### AC3: Страница создания маршрута
**Given** страница создания маршрута `/routes/new`
**When** пользователь видит страницу
**Then** отображаются breadcrumbs: "Маршруты > Новый маршрут"

### AC4: Страница Integrations
**Given** страница Integrations `/audit/integrations`
**When** пользователь видит страницу
**Then** отображаются breadcrumbs: "Журнал аудита > Интеграции"

### AC5: Позиционирование и стиль
**Given** breadcrumbs
**When** отображаются на странице
**Then** позиционируются между header и заголовком страницы
**And** используют стандартный Ant Design Breadcrumb компонент

## Tasks / Subtasks

- [x] Task 1: Создать компонент PageBreadcrumbs (AC5)
  - [x] 1.1 Создать `PageBreadcrumbs.tsx` в `src/shared/components/`
  - [x] 1.2 Интегрировать с React Router (`useLocation`, `useParams`)
  - [x] 1.3 Создать конфигурацию маршрутов для breadcrumbs
  - [x] 1.4 Добавить unit тесты (25 тестов)

- [x] Task 2: Интегрировать в MainLayout
  - [x] 2.1 Добавить PageBreadcrumbs между Header и Content
  - [x] 2.2 Скрывать на страницах без вложенности (Dashboard, Routes list, etc.)
  - [x] 2.3 Обновить стили для отступов

- [x] Task 3: Breadcrumbs для Routes (AC1, AC2, AC3)
  - [x] 3.1 `/routes` → не показывать (top-level)
  - [x] 3.2 `/routes/new` → "Маршруты > Новый маршрут"
  - [x] 3.3 `/routes/:id` → "Маршруты > {route.path}" (используется React Query cache)
  - [x] 3.4 `/routes/:id/edit` → "Маршруты > {route.path} > Редактирование"

- [x] Task 4: Breadcrumbs для Audit (AC4)
  - [x] 4.1 `/audit` → не показывать (top-level)
  - [x] 4.2 `/audit/integrations` → "Журнал аудита > Интеграции"

- [x] Task 5: Breadcrumbs для других страниц (optional)
  - [x] 5.1 Consumers, Rate Limits, Users — не имеют detail pages, breadcrumbs не требуются

## Dev Notes

### Архитектурные паттерны

**Ant Design Breadcrumb:**
```tsx
import { Breadcrumb } from 'antd'
import { Link } from 'react-router-dom'

<Breadcrumb
  items={[
    { title: <Link to="/routes">Маршруты</Link> },
    { title: <Link to={`/routes/${id}`}>/api/users</Link> },
    { title: 'Редактирование' },  // последний элемент — не ссылка
  ]}
/>
```

**React Router Integration:**
```tsx
import { useLocation, useParams, useMatches } from 'react-router-dom'

// useLocation() — текущий pathname
// useParams() — параметры (:id)
// useMatches() — все matched routes (для breadcrumb metadata)
```

**Breadcrumb Config Pattern:**
```typescript
interface BreadcrumbConfig {
  pattern: string          // '/routes/:id/edit'
  items: BreadcrumbItem[]  // статические + динамические
}

interface BreadcrumbItem {
  label: string | ((params: Record<string, string>) => string)
  path?: string | ((params: Record<string, string>) => string)
  // Если path undefined — текущий элемент (не ссылка)
}
```

**Динамические данные (route.path):**
- Для `/routes/:id` нужно получить route.path
- Варианты:
  1. Context/Store — если route уже загружен на странице
  2. React Query cache — `queryClient.getQueryData(['route', id])`
  3. Отдельный fetch — избыточно, лучше использовать cache
- Рекомендация: использовать React Query cache

### Project Structure Notes

**Новые файлы:**
```
frontend/admin-ui/src/shared/
├── components/
│   ├── PageBreadcrumbs.tsx      # основной компонент
│   └── PageBreadcrumbs.test.tsx # unit тесты
└── config/
    └── breadcrumbsConfig.ts     # конфигурация маршрутов
```

**Файлы для изменения:**
```
frontend/admin-ui/src/
├── layouts/
│   └── MainLayout.tsx           # добавить PageBreadcrumbs
└── shared/components/index.ts   # экспорт
```

### Technical Requirements

**Breadcrumb Route Config:**
```typescript
// src/shared/config/breadcrumbsConfig.ts

export const BREADCRUMB_ROUTES: BreadcrumbConfig[] = [
  // Routes
  {
    pattern: '/routes/new',
    items: [
      { label: 'Маршруты', path: '/routes' },
      { label: 'Новый маршрут' },
    ],
  },
  {
    pattern: '/routes/:id/edit',
    items: [
      { label: 'Маршруты', path: '/routes' },
      { label: (params) => getRoutePathFromCache(params.id), path: (params) => `/routes/${params.id}` },
      { label: 'Редактирование' },
    ],
  },
  {
    pattern: '/routes/:id',
    items: [
      { label: 'Маршруты', path: '/routes' },
      { label: (params) => getRoutePathFromCache(params.id) },
    ],
  },
  // Audit
  {
    pattern: '/audit/integrations',
    items: [
      { label: 'Журнал аудита', path: '/audit' },
      { label: 'Интеграции' },
    ],
  },
]
```

**Route Path from Cache:**
```typescript
import { useQueryClient } from '@tanstack/react-query'

function useRoutePath(id: string): string {
  const queryClient = useQueryClient()
  const route = queryClient.getQueryData<Route>(['route', id])
  return route?.path ?? `Маршрут ${id}`
}
```

**MainLayout Integration:**
```tsx
// MainLayout.tsx
<Layout>
  <Header>...</Header>
  <PageBreadcrumbs />  {/* Между Header и Content */}
  <Content>
    <Outlet />
  </Content>
</Layout>
```

**Стилизация:**
```tsx
// PageBreadcrumbs.tsx
<div style={{
  padding: '12px 24px',
  background: isDark ? '#1f1f1f' : '#fafafa',
  borderBottom: `1px solid ${isDark ? '#303030' : '#f0f0f0'}`,
}}>
  <Breadcrumb items={items} />
</div>
```

### Path Matching

**matchPath utility:**
```typescript
import { matchPath } from 'react-router-dom'

function findBreadcrumbConfig(pathname: string): BreadcrumbConfig | null {
  for (const config of BREADCRUMB_ROUTES) {
    const match = matchPath(config.pattern, pathname)
    if (match) {
      return { ...config, params: match.params }
    }
  }
  return null
}
```

### Testing Requirements

**Unit тесты PageBreadcrumbs:**
```typescript
describe('PageBreadcrumbs', () => {
  it('не отображает breadcrumbs на top-level страницах', () => { ... })
  it('отображает breadcrumbs для /routes/new', () => { ... })
  it('отображает breadcrumbs с route.path для /routes/:id', () => { ... })
  it('все элементы кроме последнего кликабельны', () => { ... })
  it('навигация работает при клике на breadcrumb', () => { ... })
})
```

**E2E тест (опционально):**
```typescript
test('breadcrumbs навигация на странице редактирования', async ({ page }) => {
  await page.goto('/routes/123/edit')
  await expect(page.getByRole('navigation', { name: 'breadcrumb' })).toBeVisible()
  await page.getByRole('link', { name: 'Маршруты' }).click()
  await expect(page).toHaveURL('/routes')
})
```

### References

- [Source: _bmad-output/planning-artifacts/epics.md — Story 16.6]
- [Source: frontend/admin-ui/src/layouts/MainLayout.tsx — layout structure]
- [Source: frontend/admin-ui/src/App.tsx — route definitions]
- [Ant Design Breadcrumb](https://ant.design/components/breadcrumb)
- [React Router matchPath](https://reactrouter.com/en/main/utils/match-path)

### Route Structure Reference

| Route | Breadcrumbs | Notes |
|-------|-------------|-------|
| `/dashboard` | — | top-level, не показывать |
| `/routes` | — | top-level |
| `/routes/new` | Маршруты > Новый маршрут | |
| `/routes/:id` | Маршруты > {path} | динамический path |
| `/routes/:id/edit` | Маршруты > {path} > Редактирование | |
| `/users` | — | top-level |
| `/consumers` | — | top-level |
| `/rate-limits` | — | top-level |
| `/approvals` | — | top-level |
| `/audit` | — | top-level |
| `/audit/integrations` | Журнал аудита > Интеграции | |
| `/metrics` | — | top-level |
| `/test` | — | top-level |

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- Unit тесты: 25 тестов для PageBreadcrumbs (все прошли)
- Полный тест suite: 803+ тестов (все прошли)

### Completion Notes List

1. Создан компонент `PageBreadcrumbs.tsx`:
   - Использует Ant Design Breadcrumb компонент
   - Интегрирован с React Router (useLocation, useParams, matchPath)
   - Получает route.path из React Query cache
   - Поддерживает тёмную и светлую тему

2. Создана конфигурация `breadcrumbsConfig.ts`:
   - Определены паттерны для Routes (/routes/new, /routes/:id, /routes/:id/edit)
   - Определены паттерны для Audit (/audit/integrations)
   - Поддержка динамических label через функции

3. Интеграция в MainLayout:
   - PageBreadcrumbs позиционирован между Header и Content
   - Автоматически скрывается на top-level страницах

4. Тесты покрывают все AC:
   - AC1: /routes/:id/edit breadcrumbs с кликабельными ссылками
   - AC2: /routes/:id breadcrumbs
   - AC3: /routes/new breadcrumbs
   - AC4: /audit/integrations breadcrumbs
   - AC5: Ant Design Breadcrumb, правильное позиционирование, темы

5. Accessibility improvements (Code Review):
   - Добавлен `aria-label="Навигация"` на Breadcrumb nav элемент
   - Добавлен `aria-current="page"` на последний элемент breadcrumb
   - Добавлены тесты для edge cases (спецсимволы в route.path, несуществующие маршруты)

### Change Log

| Date | Author | Description |
|------|--------|-------------|
| 2026-03-04 | SM | Story created — ready for dev |
| 2026-03-04 | Dev Agent | Implemented breadcrumbs navigation (all tasks complete) |
| 2026-03-04 | Code Review | Fixed accessibility issues (aria-label, aria-current), added edge case tests |

### File List

**New files:**
- `frontend/admin-ui/src/shared/components/PageBreadcrumbs.tsx` — основной компонент
- `frontend/admin-ui/src/shared/components/PageBreadcrumbs.test.tsx` — 25 unit тестов
- `frontend/admin-ui/src/shared/config/breadcrumbsConfig.ts` — конфигурация маршрутов

**Modified files:**
- `frontend/admin-ui/src/shared/components/index.ts` — экспорт PageBreadcrumbs
- `frontend/admin-ui/src/shared/providers/ThemeProvider.tsx` — экспорт ThemeContext для тестов
- `frontend/admin-ui/src/shared/providers/index.ts` — экспорт ThemeContext
- `frontend/admin-ui/src/layouts/MainLayout.tsx` — интеграция PageBreadcrumbs
- `frontend/admin-ui/src/layouts/MainLayout.test.tsx` — обновлён мок для shared components
