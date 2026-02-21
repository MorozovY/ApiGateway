# Story 8.7: Расширить поиск Approvals на Upstream URL

Status: done

## Story

As a **Security Specialist**,
I want search on Approvals page to include Upstream URL,
so that I can find pending routes by upstream service.

## Acceptance Criteria

**AC1 — Поиск фильтрует по Path OR Upstream URL:**

**Given** пользователь с ролью security или admin находится на странице `/approvals`
**When** пользователь вводит "payment" в поле поиска
**Then** таблица pending маршрутов фильтруется где Path OR Upstream URL содержит "payment"
**And** поиск регистронезависимый (case-insensitive)

**AC2 — Placeholder обновлён:**

**Given** пользователь видит поле поиска на странице `/approvals`
**When** поле пустое
**Then** placeholder показывает "Поиск по path, upstream..."

## Tasks / Subtasks

- [x] Task 1: Frontend — расширить фильтрацию на upstreamUrl (AC1)
  - [x] Subtask 1.1: Изменить filteredRoutes useMemo в ApprovalsPage.tsx
  - [x] Subtask 1.2: Добавить проверку upstreamUrl в filter callback

- [x] Task 2: Frontend — обновить placeholder (AC2)
  - [x] Subtask 2.1: Изменить placeholder в Input компоненте

- [x] Task 3: Тесты
  - [x] Subtask 3.1: Добавить тест — поиск по upstream URL
  - [x] Subtask 3.2: Добавить тест — поиск case-insensitive
  - [x] Subtask 3.3: Добавить тест — placeholder показывает новый текст
  - [x] Subtask 3.4: Все 14 тестов проходят ✅

## API Dependencies Checklist

**Эта story НЕ требует backend изменений.**

Фильтрация происходит на клиенте — `usePendingRoutes()` загружает все pending маршруты, затем `filteredRoutes` useMemo применяет клиентскую фильтрацию.

**Почему клиентская фильтрация:**
- Pending маршрутов обычно немного (< 100)
- Данные уже содержат upstreamUrl
- API `/api/v1/routes/pending` не поддерживает параметр search

## Dev Notes

### Текущее состояние кода

**ApprovalsPage.tsx:56-64:**
```tsx
// Текущая реализация — ищет только по path (Story 5.7, AC2)
const filteredRoutes = useMemo(() => {
  if (!pendingRoutes || !searchText.trim()) {
    return pendingRoutes
  }
  const lowerSearch = searchText.toLowerCase()
  return pendingRoutes.filter((route) =>
    route.path.toLowerCase().includes(lowerSearch)
  )
}, [pendingRoutes, searchText])
```

**Проблема:** По AC нужно искать по `path OR upstreamUrl`, а не только по `path`.

**ApprovalsPage.tsx:221-229:**
```tsx
<Input
  placeholder="Поиск по пути..."
  prefix={<SearchOutlined />}
  value={searchText}
  onChange={(e) => setSearchText(e.target.value)}
  allowClear
  style={{ marginBottom: 16, maxWidth: 300 }}
  data-testid="search-input"
/>
```

**Проблема:** Placeholder показывает только "пути", но после изменения будет искать и по upstream.

### Решение

**1. ApprovalsPage.tsx — изменить фильтрацию (lines 56-64):**

```tsx
// Было:
const filteredRoutes = useMemo(() => {
  if (!pendingRoutes || !searchText.trim()) {
    return pendingRoutes
  }
  const lowerSearch = searchText.toLowerCase()
  return pendingRoutes.filter((route) =>
    route.path.toLowerCase().includes(lowerSearch)
  )
}, [pendingRoutes, searchText])

// Стало:
const filteredRoutes = useMemo(() => {
  if (!pendingRoutes || !searchText.trim()) {
    return pendingRoutes
  }
  const lowerSearch = searchText.toLowerCase()
  return pendingRoutes.filter((route) =>
    route.path.toLowerCase().includes(lowerSearch) ||
    route.upstreamUrl.toLowerCase().includes(lowerSearch)
  )
}, [pendingRoutes, searchText])
```

**2. ApprovalsPage.tsx — изменить placeholder (line 222):**

```tsx
// Было:
placeholder="Поиск по пути..."

// Стало:
placeholder="Поиск по path, upstream..."
```

### Тесты

**Добавить в ApprovalsPage.test.tsx:**

```typescript
it('поиск фильтрует по path', async () => {
  renderWithMockAuth(<ApprovalsPage />, {
    authValue: { isAuthenticated: true, user: securityUser },
  })

  await waitFor(() => {
    expect(screen.getByText('/api/orders')).toBeInTheDocument()
    expect(screen.getByText('/api/payments')).toBeInTheDocument()
  })

  // Вводим текст в поле поиска — соответствует path первого маршрута
  const searchInput = screen.getByTestId('search-input')
  fireEvent.change(searchInput, { target: { value: 'orders' } })

  await waitFor(() => {
    expect(screen.getByText('/api/orders')).toBeInTheDocument()
    expect(screen.queryByText('/api/payments')).not.toBeInTheDocument()
  })
})

it('поиск фильтрует по upstream URL (Story 8.7, AC1)', async () => {
  renderWithMockAuth(<ApprovalsPage />, {
    authValue: { isAuthenticated: true, user: securityUser },
  })

  await waitFor(() => {
    expect(screen.getByText('/api/orders')).toBeInTheDocument()
    expect(screen.getByText('/api/payments')).toBeInTheDocument()
  })

  // Вводим текст в поле поиска — соответствует upstream URL второго маршрута
  const searchInput = screen.getByTestId('search-input')
  fireEvent.change(searchInput, { target: { value: 'payment-service' } })

  await waitFor(() => {
    expect(screen.queryByText('/api/orders')).not.toBeInTheDocument()
    expect(screen.getByText('/api/payments')).toBeInTheDocument()
  })
})

it('поиск case-insensitive (Story 8.7, AC1)', async () => {
  renderWithMockAuth(<ApprovalsPage />, {
    authValue: { isAuthenticated: true, user: securityUser },
  })

  await waitFor(() => {
    expect(screen.getByText('/api/orders')).toBeInTheDocument()
  })

  // Вводим текст в верхнем регистре
  const searchInput = screen.getByTestId('search-input')
  fireEvent.change(searchInput, { target: { value: 'ORDER-SERVICE' } })

  await waitFor(() => {
    expect(screen.getByText('/api/orders')).toBeInTheDocument()
    expect(screen.queryByText('/api/payments')).not.toBeInTheDocument()
  })
})

it('placeholder показывает "Поиск по path, upstream..." (Story 8.7, AC2)', async () => {
  renderWithMockAuth(<ApprovalsPage />, {
    authValue: { isAuthenticated: true, user: securityUser },
  })

  await waitFor(() => {
    expect(screen.getByPlaceholderText('Поиск по path, upstream...')).toBeInTheDocument()
  })
})
```

### Project Structure Notes

| Файл | Путь | Изменение |
|------|------|-----------|
| ApprovalsPage.tsx | `frontend/admin-ui/src/features/approval/components/` | Изменить filteredRoutes + placeholder |
| ApprovalsPage.test.tsx | `frontend/admin-ui/src/features/approval/components/` | Добавить 4 теста для search |

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 8.7]
- [Source: frontend/admin-ui/src/features/approval/components/ApprovalsPage.tsx:56-64] — текущий filteredRoutes
- [Source: frontend/admin-ui/src/features/approval/components/ApprovalsPage.tsx:221-229] — поле поиска
- [Source: _bmad-output/implementation-artifacts/8-6-audit-logs-user-combobox-fix.md] — предыдущая story
- [Source: _bmad-output/implementation-artifacts/8-5-routes-search-path-upstream.md] — аналогичная story для Routes

### Тестовые команды

```bash
# Frontend unit тесты
cd frontend/admin-ui
npm run test:run -- ApprovalsPage

# Все frontend тесты
cd frontend/admin-ui && npm run test:run
```

### Связанные stories

- Story 5.7 — добавление поиска по path на страницу Approvals (текущая реализация)
- Story 8.5 — аналогичное расширение поиска для Routes page
- Story 4.6 — базовая реализация страницы Approvals

### Паттерны из предыдущих stories

**Story 8.5 показала паттерн:** расширение поиска с одного поля на несколько требует:
1. Добавить OR условие в filter/SQL
2. Обновить placeholder чтобы показать все searchable поля
3. Добавить тесты для нового поля

**Отличие от 8.5:** Story 8.5 изменяла backend SQL, эта story — только frontend (клиентская фильтрация).

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

N/A

### Completion Notes List

- ✅ Frontend: Расширена фильтрация в useMemo — теперь ищет по `path OR upstreamUrl` (AC1)
- ✅ Frontend: Обновлён placeholder на "Поиск по path, upstream..." (AC2)
- ✅ Добавлено 4 новых теста в describe block "Поиск по path и upstream URL (Story 8.7)"
- ✅ Все 14 тестов ApprovalsPage проходят

### File List

**Modified:**
- `frontend/admin-ui/src/features/approval/components/ApprovalsPage.tsx` (filter logic + placeholder)
- `frontend/admin-ui/src/features/approval/components/ApprovalsPage.test.tsx` (4 новых теста)

## Change Log

- 2026-02-21: Story 8.7 implemented — search on Approvals page now filters by path OR upstream URL

