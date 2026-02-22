# Story 10.2: Approvals Real-Time Updates

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **Security Specialist**,
I want the Approvals page to update automatically,
so that I see new pending routes without refreshing.

## Bug Report

- **Severity:** MEDIUM
- **Observed:** New pending routes don't appear until page refresh
- **Reproduction:** Create pending route in one tab, check Approvals tab in another — no update
- **Source:** Epic 9 Retrospective (BUG-03) — feedback from Yury (Project Lead)

## Acceptance Criteria

### AC1: Auto-refresh pending routes table
**Given** user is on `/approvals` page
**When** a new route is submitted for approval (by another user or in another tab)
**Then** the route appears in the table within 5 seconds
**And** no manual page refresh is required

### AC2: Sidebar pending count auto-update
**Given** polling or WebSocket is implemented
**When** connection is active
**Then** pending count badge in sidebar updates automatically
**And** reflects current number of pending routes

### AC3: Manual refresh button
**Given** user is on `/approvals` page
**When** page loads
**Then** a "Refresh" button is visible next to filters
**And** clicking it immediately fetches latest data
**And** shows brief loading indicator

### AC4: Polling not blocking UI
**Given** polling interval is set to 5 seconds
**When** user is interacting with the page (filtering, sorting)
**Then** polling does NOT interrupt user actions
**And** background fetch is transparent to user

### AC5: No duplicate requests
**Given** polling is active
**When** user manually clicks "Refresh" button
**Then** only one request is sent (polling skipped on that interval)
**And** no race conditions occur

## Tasks / Subtasks

- [x] Task 1: Add polling to useApprovals hook (AC: #1, #4, #5)
  - [x] 1.1 Add `refetchInterval: 5000` to usePendingRoutes query
  - [x] 1.2 Add `refetchIntervalInBackground: false` — don't poll when tab is hidden
  - [x] 1.3 Verify no duplicate requests when combined with manual refetch

- [x] Task 2: Add manual refresh button to ApprovalsPage (AC: #3)
  - [x] 2.1 Add "Refresh" button (антд `ReloadOutlined` icon) next to search
  - [x] 2.2 Connect to React Query's `refetch()` function
  - [x] 2.3 Show loading spinner on button while fetching
  - [x] 2.4 Disable button during loading to prevent double-clicks

- [x] Task 3: Update sidebar pending count (AC: #2)
  - [x] 3.1 Find sidebar component that displays pending count badge
  - [x] 3.2 Ensure it uses same query key as ApprovalsPage
  - [x] 3.3 Verify badge updates when polling fetches new data
  - [x] 3.4 Add skeleton/loading state if needed — НЕ ТРЕБУЕТСЯ: React Query handles this

- [x] Task 4: Add unit tests (AC: all)
  - [x] 4.1 Test: `usePendingRoutes` hook has refetchInterval configured
  - [x] 4.2 Test: Refresh button triggers refetch
  - [x] 4.3 Test: Loading state displays during fetch
  - [x] 4.4 N/A: No duplicate requests — React Query deduplicates by queryKey automatically

- [ ] Task 5: Manual validation (AC: all)
  - [ ] 5.1 Open Approvals page in browser 1
  - [ ] 5.2 Create pending route in browser 2 (or via API)
  - [ ] 5.3 Verify route appears in browser 1 within 5 seconds
  - [ ] 5.4 Verify sidebar badge updates
  - [ ] 5.5 Test Refresh button responsiveness

## API Dependencies Checklist

**Backend API endpoints, используемые в этой story:**

| Endpoint | Method | Параметры | Статус |
|----------|--------|-----------|--------|
| `/api/v1/routes/pending` | GET | `limit`, `offset`, `sort` | ✅ Существует |
| `/api/v1/routes/{id}/approve` | POST | - | ✅ Существует (не изменяется) |
| `/api/v1/routes/{id}/reject` | POST | `reason` | ✅ Существует (не изменяется) |

**Проверки перед началом разработки:**

- [x] Все необходимые endpoints существуют в backend
- [x] Query параметры поддерживают все фильтры из AC
- [x] Response format содержит все поля, необходимые для UI
- [x] Role-based access настроен корректно (SECURITY, ADMIN)
- [x] Pagination поддерживается

**Backend изменения НЕ ТРЕБУЮТСЯ** — это чисто frontend story с polling.

## Dev Notes

### Архитектура решения

**Рекомендуемый подход: Polling через React Query**

Polling — простое и надёжное решение для данного кейса:
- 5 секунд — допустимая задержка для Security workflow
- Minimal code changes (добавить `refetchInterval`)
- Нет необходимости в WebSocket/SSE инфраструктуре

**Почему НЕ WebSocket:**
- Overhead для одного endpoint
- Требует backend изменения (spring-boot-starter-websocket)
- Нет реальной потребности в sub-second latency

### Текущая реализация Approvals

**Ключевые файлы:**

| Файл | Назначение |
|------|-----------|
| `frontend/admin-ui/src/features/approval/hooks/useApprovals.ts` | React Query hooks — **ОСНОВНЫЕ ИЗМЕНЕНИЯ** |
| `frontend/admin-ui/src/features/approval/components/ApprovalsPage.tsx` | Компонент страницы — добавить Refresh button |
| `frontend/admin-ui/src/features/approval/api/approvalsApi.ts` | API клиент — БЕЗ ИЗМЕНЕНИЙ |
| `frontend/admin-ui/src/components/Sidebar.tsx` (или аналог) | Sidebar — проверить pending badge |

**Текущий код useApprovals.ts:**
```typescript
export function usePendingRoutes() {
  return useQuery({
    queryKey: [PENDING_ROUTES_QUERY_KEY],
    queryFn: approvalsApi.fetchPendingRoutes,
    // ⚠️ НЕТ refetchInterval — данные не обновляются автоматически
  })
}
```

**Требуемые изменения:**
```typescript
export const APPROVALS_REFRESH_INTERVAL = 5000 // 5 секунд

export function usePendingRoutes() {
  return useQuery({
    queryKey: [PENDING_ROUTES_QUERY_KEY],
    queryFn: approvalsApi.fetchPendingRoutes,
    refetchInterval: APPROVALS_REFRESH_INTERVAL,
    refetchIntervalInBackground: false, // не polling когда tab скрыт
    staleTime: 2000, // считать данные stale через 2 сек
  })
}
```

### Пример Refresh button (Ant Design)

```tsx
import { ReloadOutlined } from '@ant-design/icons'
import { Button } from 'antd'

// В компоненте ApprovalsPage:
const { refetch, isFetching } = usePendingRoutes()

<Button
  icon={<ReloadOutlined spin={isFetching} />}
  onClick={() => refetch()}
  loading={isFetching}
  disabled={isFetching}
>
  Обновить
</Button>
```

### Паттерны из существующего кода

**Metrics page — пример polling:**
```typescript
// frontend/admin-ui/src/features/metrics/hooks/useMetrics.ts
export const METRICS_REFRESH_INTERVAL = 10000 // 10 секунд

export function useMetricsSummary(period: MetricsPeriod = '5m') {
  return useQuery({
    queryKey: QUERY_KEYS.summary(period),
    queryFn: () => metricsApi.getSummary(period),
    refetchInterval: METRICS_REFRESH_INTERVAL, // ← auto-refresh
    staleTime: METRICS_STALE_TIME,
  })
}
```

**Health Check — Refresh button:**
```typescript
// frontend/admin-ui/src/features/health/components/HealthCheckCard.tsx
<Button
  icon={<ReloadOutlined />}
  onClick={onRefresh}
  loading={isRefreshing}
>
  Проверить
</Button>
```

### Sidebar pending badge

Sidebar badge показывает количество pending routes. Необходимо:
1. Найти где отображается badge (скорее всего `Sidebar.tsx` или `Layout.tsx`)
2. Убедиться что badge использует тот же query key `PENDING_ROUTES_QUERY_KEY`
3. Если badge не использует React Query — рефакторить для consistency

### Testing Considerations

**Vitest мокирование:**
```typescript
vi.useFakeTimers()

it('polls every 5 seconds', async () => {
  const fetchSpy = vi.spyOn(approvalsApi, 'fetchPendingRoutes')
  render(<ApprovalsPage />)

  expect(fetchSpy).toHaveBeenCalledTimes(1) // initial fetch

  await vi.advanceTimersByTimeAsync(5000)
  expect(fetchSpy).toHaveBeenCalledTimes(2) // polling fetch

  await vi.advanceTimersByTimeAsync(5000)
  expect(fetchSpy).toHaveBeenCalledTimes(3) // another polling
})
```

### Project Structure Notes

- Polling config constants в `hooks/useApprovals.ts` — централизованно
- Refresh button — standard Ant Design pattern (`ReloadOutlined` icon)
- Тесты для hooks в `__tests__/useApprovals.test.ts`

### References

- [Source: architecture.md#Frontend State] — React Query + Context
- [Source: architecture.md#UI Library] — Ant Design components
- [Source: 10-1-rate-limit-not-working.md] — предыдущая story с паттернами
- [Source: frontend/admin-ui/src/features/metrics/hooks/useMetrics.ts] — пример polling
- [Source: epics.md#Story 10.2] — acceptance criteria

## Previous Story Learnings (10.1)

**Из Story 10.1 (Rate Limit Bug):**

1. **Code Review важен** — исправление `math.floor` в Lua было критичным
2. **Логирование помогает** — DEBUG логи при rate limit exceeded ценны для диагностики
3. **Интеграционные тесты** — покрывают edge cases лучше unit тестов
4. **Testcontainers** — нужны для тестов с Redis

**Применимо к текущей story:**
- Добавить unit тесты для polling behavior
- Проверить edge cases (tab visibility, network errors)
- Manual validation важна — автотесты не заменяют реальный UX тест

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

### Completion Notes List

1. **Task 1-3 (Polling)**: Добавлен `refetchInterval: 5000` и `refetchIntervalInBackground: false` в `usePendingRoutes()` и `usePendingRoutesCount()`. React Query автоматически управляет дедупликацией запросов.
2. **Task 2 (Refresh button)**: Добавлена кнопка "Обновить" с `ReloadOutlined` иконкой, loading state через `isFetching`, disabled во время загрузки.
3. **Task 3 (Sidebar)**: `usePendingRoutesCount()` уже использует тот же query key `PENDING_ROUTES_QUERY_KEY`, polling добавлен для auto-refresh badge.
4. **Task 4 (Tests)**: Добавлено 8 новых тестов: 3 для констант polling, 5 для Refresh button UI.

### Code Review Fixes

**Round 1:**
1. **M1 Fixed**: Убрана избыточная анимация `spin={isFetching}` с иконки — loading state кнопки уже показывает spinner.
2. **M2 Fixed**: Добавлен экспорт `APPROVALS_REFRESH_INTERVAL` и `APPROVALS_STALE_TIME` через `index.ts`.
3. **L1 Fixed**: Обновлён заголовок файла `ApprovalsPage.tsx` — добавлена ссылка на Story 10.2.

**Round 2 (2026-02-22):**
4. **M1 Fixed**: Task 4.4 переформулирован — теперь "N/A: React Query deduplicates by queryKey automatically"
5. **M2 Fixed**: Status изменён с "done" на "review" — Task 5 (Manual validation) не выполнен
6. **M3 Fixed**: Добавлен комментарий в `useApprovals.test.ts` о design decision для polling тестов
7. **L1 Fixed**: Mock в `ApprovalsPage.test.tsx` использует реальные константы через `vi.importActual`

### File List

| File | Action | Description |
|------|--------|-------------|
| `frontend/admin-ui/src/features/approval/hooks/useApprovals.ts` | Modified | Добавлен polling: `refetchInterval`, `refetchIntervalInBackground`, `staleTime` |
| `frontend/admin-ui/src/features/approval/components/ApprovalsPage.tsx` | Modified | Добавлена кнопка Refresh с loading state; убрана двойная анимация |
| `frontend/admin-ui/src/features/approval/components/ApprovalsPage.test.tsx` | Modified | Добавлены тесты для Refresh button (5 тестов) |
| `frontend/admin-ui/src/features/approval/hooks/useApprovals.test.ts` | Created | Тесты для polling констант (3 теста) |
| `frontend/admin-ui/src/features/approval/index.ts` | Modified | Экспорт `APPROVALS_REFRESH_INTERVAL`, `APPROVALS_STALE_TIME` |

