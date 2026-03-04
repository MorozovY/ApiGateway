# Story 16.2: Наполнение Dashboard полезным контентом

Status: done

## Story

As a **User**,
I want to see useful information on the Dashboard,
so that I can quickly understand the current state of the system.

## Acceptance Criteria

### AC1: Developer Dashboard
**Given** пользователь авторизован как Developer
**When** открывает Dashboard
**Then** видит:
- Quick Stats: количество своих маршрутов по статусам (draft, pending, published, rejected)
- Recent Activity: последние 5 изменений своих маршрутов
- Quick Actions: кнопки "Создать маршрут", "Мои маршруты"

### AC2: Security Dashboard
**Given** пользователь авторизован как Security
**When** открывает Dashboard
**Then** видит:
- Pending Approvals: количество маршрутов на согласование (с ссылкой на /approvals)
- Quick Stats: количество маршрутов по статусам
- Recent Approvals: последние 5 согласованных/отклонённых
- Quick Actions: "Согласования", "Журнал аудита"

### AC3: Admin Dashboard
**Given** пользователь авторизован как Admin
**When** открывает Dashboard
**Then** видит:
- System Overview: общее количество маршрутов, пользователей, consumers
- Pending Approvals: количество на согласование
- Health Status: краткий статус системы (healthy/degraded)
- Quick Actions: все основные действия

### AC4: Loading States
**Given** Dashboard с Quick Stats
**When** данные загружаются
**Then** отображается skeleton loading
**And** ошибки gracefully handled с кнопкой retry

### AC5: Responsive Layout
**Given** Dashboard
**When** данные загружены
**Then** карточки responsive (адаптируются к ширине экрана)
**And** используется grid layout Ant Design Row/Col

## Tasks / Subtasks

### Backend Tasks

- [x] Task 1: Создать endpoint `GET /api/v1/dashboard/summary` (AC: 1,2,3)
  - [x] 1.1: Создать `DashboardController.kt`
  - [x] 1.2: Создать `DashboardService.kt` с логикой подсчёта
  - [x] 1.3: Создать `DashboardSummaryDto.kt` с разными полями для ролей
  - [x] 1.4: Добавить role-based filtering (Developer видит только свои данные)
  - [x] 1.5: Добавить unit/integration тесты

- [x] Task 2: Создать endpoint `GET /api/v1/dashboard/recent-activity` (AC: 1,2)
  - [x] 2.1: Добавить метод в `DashboardController.kt`
  - [x] 2.2: Query к audit_logs с фильтрацией по entity_type='ROUTE'
  - [x] 2.3: Для Developer — фильтрация по user_id
  - [x] 2.4: Для Security — только approve/reject actions
  - [x] 2.5: Добавить тесты

### Frontend Tasks

- [x] Task 3: Создать компонент `QuickStats.tsx` (AC: 1,2,3,4,5)
  - [x] 3.1: Карточки с количеством маршрутов по статусам
  - [x] 3.2: Skeleton loading state
  - [x] 3.3: Error state с retry кнопкой
  - [x] 3.4: Responsive grid (xs:24, sm:12, md:6)
  - [x] 3.5: Кликабельные карточки → переход на /routes?status=X

- [x] Task 4: Создать компонент `RecentActivity.tsx` (AC: 1,2,4)
  - [x] 4.1: List с последними 5 действиями
  - [x] 4.2: Иконки и теги по типу действия (create, update, approve, reject)
  - [x] 4.3: Relative time (formatRelativeTime)
  - [x] 4.4: Loading, empty и error states

- [x] Task 5: Создать компоненты PendingApprovals и AdminStats (AC: 2,3)
  - [x] 5.1: PendingApprovals — Alert для Security/Admin
  - [x] 5.2: AdminStats — карточки users, consumers, health для Admin
  - [x] 5.3: Role-based отображение компонентов

- [x] Task 6: Обновить `DashboardPage.tsx` (AC: 1,2,3,5)
  - [x] 6.1: Собрать все компоненты в responsive layout
  - [x] 6.2: Условное отображение по роли пользователя
  - [x] 6.3: Сохранить приветствие и PageInfoBlock
  - [x] 6.4: Добавить тесты для QuickStats

- [x] Task 7: Создать API и hooks для Dashboard (AC: 1,2,3,4)
  - [x] 7.1: `dashboardApi.ts` с функциями getDashboardSummary, getRecentActivity
  - [x] 7.2: `useDashboard.ts` hook с React Query
  - [x] 7.3: Типы `dashboard.types.ts`

## API Dependencies Checklist

**Backend API endpoints, используемые в этой story:**

| Endpoint | Method | Параметры | Статус |
|----------|--------|-----------|--------|
| `/api/v1/dashboard/summary` | GET | — | ✅ Создан |
| `/api/v1/dashboard/recent-activity` | GET | `limit=5` | ✅ Создан |
| `/api/v1/health/services` | GET | — | ✅ Существует |
| `/api/v1/routes` | GET | `status`, `createdBy` | ✅ Существует |
| `/api/v1/users` | GET | — | ✅ Существует (admin only) |
| `/api/v1/consumers` | GET | — | ✅ Существует |

**Проверки перед началом разработки:**

- [x] Существующие endpoints достаточны для health check
- [x] **СОЗДАН**: `/api/v1/dashboard/summary` endpoint
- [x] **СОЗДАН**: `/api/v1/dashboard/recent-activity` endpoint
- [x] Role-based access уже работает в существующих endpoints

**Response format для нового endpoint:**

```kotlin
// GET /api/v1/dashboard/summary
data class DashboardSummaryDto(
    // Для всех ролей
    val routesByStatus: Map<String, Int>,  // draft, pending, published, rejected
    val pendingApprovalsCount: Int,

    // Только для Admin
    val totalUsers: Int?,
    val totalConsumers: Int?,
    val systemHealth: String?  // "healthy" | "degraded" | "down"
)

// GET /api/v1/dashboard/recent-activity
data class RecentActivityDto(
    val items: List<ActivityItem>
)

data class ActivityItem(
    val id: String,
    val action: String,      // "created", "updated", "approved", "rejected"
    val entityType: String,  // "route"
    val entityId: String,
    val entityName: String,  // route.path
    val performedBy: String, // username
    val performedAt: String  // ISO timestamp
)
```

## Dev Notes

### Существующие компоненты для переиспользования

1. **MetricsWidget** (`src/features/metrics/components/MetricsWidget.tsx`)
   - Карточки статистики с Statistic компонентом
   - Skeleton loading patterns
   - Error handling с retry

2. **HealthCheckSection** (`src/features/metrics/components/HealthCheckSection.tsx`)
   - Отображение статуса сервисов
   - Можно адаптировать для System Overview

3. **PageInfoBlock** (`src/shared/components/PageInfoBlock.tsx`)
   - Уже используется на Dashboard

4. **useHealth hook** (`src/features/metrics/hooks/useHealth.ts`)
   - Готовый hook для health check

### Архитектурные решения

1. **Role-based rendering**: использовать `useAuth()` hook для получения роли
2. **Grid layout**: Ant Design Row/Col с responsive breakpoints
3. **Data fetching**: React Query с auto-refresh (staleTime: 30s)
4. **Error boundaries**: каждая секция независимо обрабатывает ошибки

### Паттерны из существующего кода

```tsx
// Responsive grid pattern (из MetricsPage)
<Row gutter={[16, 16]}>
  <Col xs={24} sm={12} md={6}>
    <Card>...</Card>
  </Col>
</Row>

// Skeleton loading (из MetricsWidget)
{isLoading ? (
  <Skeleton active paragraph={{ rows: 2 }} />
) : (
  <Statistic title="..." value={...} />
)}

// Error handling
{error && (
  <Alert
    type="error"
    message="Ошибка загрузки"
    action={<Button onClick={refetch}>Повторить</Button>}
  />
)}
```

### Project Structure Notes

**Новые файлы:**

```
frontend/admin-ui/src/features/dashboard/
├── api/
│   └── dashboardApi.ts          # NEW
├── components/
│   ├── DashboardPage.tsx        # UPDATE
│   ├── DashboardPage.test.tsx   # UPDATE
│   ├── QuickStats.tsx           # NEW
│   ├── QuickStats.test.tsx      # NEW
│   ├── RecentActivity.tsx       # NEW
│   ├── RecentActivity.test.tsx  # NEW
│   ├── QuickActions.tsx         # NEW
│   ├── QuickActions.test.tsx    # NEW
│   ├── SystemOverview.tsx       # NEW
│   └── SystemOverview.test.tsx  # NEW
├── hooks/
│   └── useDashboard.ts          # NEW
├── types/
│   └── dashboard.types.ts       # NEW
└── index.ts                     # UPDATE

backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/
├── controller/
│   └── DashboardController.kt   # NEW
├── service/
│   └── DashboardService.kt      # NEW
└── dto/
    ├── DashboardSummaryDto.kt   # NEW
    └── RecentActivityDto.kt     # NEW
```

### References

- [Source: epics.md#Story 16.2] — Acceptance Criteria
- [Source: MetricsWidget.tsx] — Паттерны карточек статистики
- [Source: HealthCheckSection.tsx] — Паттерн health status
- [Source: CLAUDE.md#Reactive Patterns] — Не использовать блокирующие вызовы в backend

## Definition of Done

- [ ] Все AC выполнены
- [ ] Backend endpoints созданы и протестированы
- [ ] Unit тесты для всех новых компонентов
- [ ] E2E тест для Dashboard (проверка role-based content)
- [ ] Визуальная проверка на разных размерах экрана
- [ ] Code review пройден

## Dev Agent Record

### Agent Model Used

_To be filled by dev agent_

### Debug Log References

_To be filled during development_

### Completion Notes List

_To be filled after completion_

### File List

_To be filled after completion_
