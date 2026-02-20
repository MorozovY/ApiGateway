# Story 7.5: Audit Log UI

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **Security Specialist**,
I want an audit log page in the Admin UI,
so that I can review and investigate changes visually (FR21, FR22).

## Acceptance Criteria

**AC1 — Таблица аудит-логов:**

**Given** user with security/admin role navigates to `/audit`
**When** the page loads
**Then** a table displays audit log entries with columns:
- Timestamp (formatted: "Feb 11, 2026, 14:30")
- Action (badge with color coding)
- Entity Type
- Entity (link to entity if exists)
- User (username)
- Details (expandable icon)
**And** pagination is displayed (default 20 items per page)
**And** table is sorted by timestamp DESC (newest first)

**AC2 — Фильтры аудит-логов:**

**Given** filter panel is displayed above the table
**When** user interacts with filters
**Then** available filters include:
- Date range picker (from/to с presets: Last 7 days, Last 30 days, This month)
- User dropdown (searchable, загружается из /api/v1/users)
- Entity type dropdown (route, rate_limit, user)
- Action dropdown (multi-select: created, updated, deleted, approved, rejected, submitted)
**And** filters are applied immediately with debounce (300ms)
**And** active filters sync to URL query params
**And** Clear Filters button resets all filters

**AC3 — Expandable Row с деталями:**

**Given** user clicks on expand icon in a row
**When** row expands
**Then** full change details are shown:
- Before/After JSON comparison (для updated событий)
- Entity ID
- Correlation ID
- IP Address (если доступен)
**And** JSON форматирован с syntax highlighting
**And** для created/deleted показываются только соответствующие данные

**AC4 — Экспорт в CSV:**

**Given** user clicks "Export" button
**When** action is triggered
**Then** CSV file is downloaded with current filtered results
**And** filename includes date range: `audit-log-YYYY-MM-DD-to-YYYY-MM-DD.csv`
**And** all visible columns included
**And** maximum 10000 rows exported (with warning if truncated)

**AC5 — Color-coded Action Badges:**

**Given** action badges are displayed
**When** viewing the table
**Then** color coding is:
- created: green
- updated: blue
- deleted: red
- approved: green
- rejected: orange
- submitted: purple
**And** Russian labels: Создано, Обновлено, Удалено, Одобрено, Отклонено, Отправлено

**AC6 — Role-based Access:**

**Given** user with developer role attempts to access `/audit`
**When** page loads
**Then** user is redirected to home page
**And** error toast: "Недостаточно прав для просмотра аудит-логов"

**Given** user with security or admin role
**When** accessing `/audit`
**Then** page loads successfully

**AC7 — Empty State и Loading:**

**Given** no audit logs match the filters
**When** table renders
**Then** empty state is displayed with message "Нет записей для выбранных фильтров"
**And** suggestion to adjust filters

**Given** data is loading
**When** table renders
**Then** skeleton loading state is shown
**And** filters remain interactive

## Tasks / Subtasks

- [x] Task 1: Создать типы для Audit Log (AC1, AC3, AC5)
  - [x] Создать `frontend/admin-ui/src/features/audit/types/audit.types.ts`
  - [x] AuditLogEntry: id, entityType, entityId, action, user (id, username), timestamp, changes, ipAddress, correlationId
  - [x] AuditFilter: userId?, action?, entityType?, dateFrom?, dateTo?, offset?, limit?
  - [x] AuditLogsResponse: items, total, offset, limit
  - [x] AUDIT_ACTION_LABELS и AUDIT_ACTION_COLORS константы

- [x] Task 2: Создать API клиент для Audit Log (AC1, AC2, AC4)
  - [x] Создать `frontend/admin-ui/src/features/audit/api/auditApi.ts`
  - [x] fetchAuditLogs(filter: AuditFilter): Promise<AuditLogsResponse>
  - [x] Использовать axios instance из shared/utils/axios
  - [x] Обработка ошибок RFC 7807

- [x] Task 3: Создать React Query hooks (AC1, AC2)
  - [x] Создать `frontend/admin-ui/src/features/audit/hooks/useAuditLogs.ts`
  - [x] useAuditLogs(filter) — query с enabled: true
  - [x] Query key: ['audit-logs', filter]
  - [x] Использовать паттерн из useRoutes

- [x] Task 4: Создать компонент AuditFilterBar (AC2)
  - [x] Создать `frontend/admin-ui/src/features/audit/components/AuditFilterBar.tsx`
  - [x] DatePicker.RangePicker с presets
  - [x] Select для userId (async load users)
  - [x] Select для entityType
  - [x] Select mode="multiple" для action
  - [x] Debounce 300ms для всех фильтров
  - [x] Clear Filters button
  - [x] URL sync через useSearchParams

- [x] Task 5: Создать компонент AuditLogsTable (AC1, AC3, AC5)
  - [x] Создать `frontend/admin-ui/src/features/audit/components/AuditLogsTable.tsx`
  - [x] ProTable с columns: timestamp, action, entityType, entity, user, expand
  - [x] expandedRowRender для деталей (AC3)
  - [x] Action badges с цветами (AC5)
  - [x] Entity links (Link to /routes/{id} для route)
  - [x] Пагинация offset/limit
  - [x] Skeleton loading

- [x] Task 6: Создать компонент ChangesViewer (AC3)
  - [x] Создать `frontend/admin-ui/src/features/audit/components/ChangesViewer.tsx`
  - [x] JSON diff view для before/after
  - [x] Подсветка изменений (red для удалённого, green для добавленного)
  - [x] Fallback для created (только after) и deleted (только before)
  - [x] Correlation ID и IP Address display

- [x] Task 7: Создать AuditPage (AC1-AC7)
  - [x] Создать `frontend/admin-ui/src/features/audit/components/AuditPage.tsx`
  - [x] Layout: FilterBar + Table
  - [x] Export button с CSV download (AC4)
  - [x] Role check: redirect если не security/admin (AC6)
  - [x] Empty state (AC7)
  - [x] Интеграция useSearchParams для URL sync

- [x] Task 8: Реализовать CSV Export (AC4)
  - [x] Создать `frontend/admin-ui/src/features/audit/utils/exportCsv.ts`
  - [x] downloadAuditCsv(data: AuditLogEntry[], dateFrom, dateTo)
  - [x] Форматирование данных для CSV
  - [x] Генерация имени файла с датами
  - [x] Warning toast если data.length >= 10000

- [x] Task 9: Обновить роутинг и sidebar (AC6)
  - [x] Обновить `App.tsx`: заменить placeholder на `<AuditPage />`
  - [x] Добавить ProtectedRoute с requiredRole: 'security'
  - [x] Проверить что Sidebar уже содержит /audit link

- [x] Task 10: Создать index.ts для feature exports
  - [x] Создать `frontend/admin-ui/src/features/audit/index.ts`
  - [x] Export: AuditPage, useAuditLogs, AuditLogEntry types

- [x] Task 11: Unit тесты компонентов (AC1-AC5)
  - [x] Создать `AuditFilterBar.test.tsx`
  - [x] Создать `AuditLogsTable.test.tsx`
  - [x] Создать `ChangesViewer.test.tsx`
  - [x] Создать `AuditPage.test.tsx`
  - [x] Mock useAuditLogs hook
  - [x] Test: role-based redirect
  - [x] Test: filter changes trigger refetch
  - [x] Test: expand row shows details
  - [x] Test: export button calls download function

## Dev Notes

### Зависимости от предыдущих stories

**Из Story 7.1-7.2 (DONE):**
- Backend API: GET /api/v1/audit с фильтрацией
- AuditLogResponse DTO структура
- userId, action, entityType, dateFrom, dateTo query params

**Из Story 7.3 (DONE):**
- GET /api/v1/routes/{routeId}/history API (для future Story 7.6)

**Из Story 7.4 (DONE):**
- GET /api/v1/routes/upstreams API (для future Story 7.6)

### Существующая frontend архитектура

**Паттерны из RoutesTable.tsx:**
```typescript
// URL sync с useSearchParams
const [searchParams, setSearchParams] = useSearchParams()
const params: AuditFilter = useMemo(() => ({
  offset: Number(searchParams.get('offset')) || 0,
  limit: Number(searchParams.get('limit')) || DEFAULT_PAGE_SIZE,
  userId: searchParams.get('userId') || undefined,
  // ...
}), [searchParams])

const { data, isLoading, error } = useAuditLogs(params)
```

**Паттерны из ApprovalsPage.tsx:**
```typescript
// Drawer для деталей
<Drawer width={600} open={drawerVisible}>
  <Descriptions column={1} bordered>
    <Descriptions.Item label="Changes">
      <pre>{JSON.stringify(entry.changes, null, 2)}</pre>
    </Descriptions.Item>
  </Descriptions>
</Drawer>
```

### Backend API Response

**GET /api/v1/audit:**
```typescript
// Request: GET /api/v1/audit?userId=UUID&action=string&entityType=string&dateFrom=2026-01-01&dateTo=2026-02-01&offset=0&limit=50
// Response:
{
  items: [
    {
      id: "uuid",
      entityType: "route",        // "route", "user", "rate_limit"
      entityId: "uuid",
      action: "created",           // "created", "updated", "deleted", "approved", "rejected", "submitted"
      user: { id: "uuid", username: "developer1" },
      timestamp: "2026-02-11T14:30:00Z",
      changes: { "before": {...}, "after": {...} },  // nullable для created/deleted
      ipAddress: "192.168.1.1",
      correlationId: "abc-123-def"
    }
  ],
  total: 150,
  offset: 0,
  limit: 20
}
```

### UI Components структура

```
frontend/admin-ui/src/features/audit/
├── api/
│   └── auditApi.ts              # fetchAuditLogs(filter)
├── components/
│   ├── AuditPage.tsx            # Main page component
│   ├── AuditPage.test.tsx
│   ├── AuditFilterBar.tsx       # Filters + Date Range
│   ├── AuditFilterBar.test.tsx
│   ├── AuditLogsTable.tsx       # Table with expandable rows
│   ├── AuditLogsTable.test.tsx
│   ├── ChangesViewer.tsx        # JSON diff for changes
│   └── ChangesViewer.test.tsx
├── hooks/
│   └── useAuditLogs.ts          # React Query hook
├── types/
│   └── audit.types.ts           # TypeScript interfaces
├── utils/
│   └── exportCsv.ts             # CSV export utility
├── config/
│   └── auditConfig.ts           # Constants, colors, labels
└── index.ts                     # Public exports
```

### Константы для Action Colors/Labels

```typescript
// config/auditConfig.ts
export const AUDIT_ACTION_LABELS: Record<string, string> = {
  created: 'Создано',
  updated: 'Обновлено',
  deleted: 'Удалено',
  approved: 'Одобрено',
  rejected: 'Отклонено',
  submitted: 'Отправлено',
}

export const AUDIT_ACTION_COLORS: Record<string, string> = {
  created: 'green',
  updated: 'blue',
  deleted: 'red',
  approved: 'green',
  rejected: 'orange',
  submitted: 'purple',
}

export const ENTITY_TYPE_LABELS: Record<string, string> = {
  route: 'Маршрут',
  user: 'Пользователь',
  rate_limit: 'Rate Limit',
}
```

### DatePicker с Presets

```tsx
import { DatePicker } from 'antd'
import dayjs from 'dayjs'

const presets = [
  { label: 'Последние 7 дней', value: [dayjs().subtract(7, 'd'), dayjs()] },
  { label: 'Последние 30 дней', value: [dayjs().subtract(30, 'd'), dayjs()] },
  { label: 'Этот месяц', value: [dayjs().startOf('month'), dayjs()] },
]

<DatePicker.RangePicker
  presets={presets}
  format="YYYY-MM-DD"
  onChange={(dates) => {
    updateParams({
      dateFrom: dates?.[0]?.format('YYYY-MM-DD'),
      dateTo: dates?.[1]?.format('YYYY-MM-DD'),
    })
  }}
/>
```

### CSV Export Implementation

```typescript
// utils/exportCsv.ts
export function downloadAuditCsv(
  data: AuditLogEntry[],
  dateFrom?: string,
  dateTo?: string
): void {
  const MAX_EXPORT = 10000
  if (data.length >= MAX_EXPORT) {
    message.warning(`Экспорт ограничен ${MAX_EXPORT} записями`)
  }

  const headers = ['ID', 'Timestamp', 'Action', 'Entity Type', 'Entity ID', 'User', 'IP Address', 'Correlation ID']
  const rows = data.slice(0, MAX_EXPORT).map(entry => [
    entry.id,
    dayjs(entry.timestamp).format('YYYY-MM-DD HH:mm:ss'),
    entry.action,
    entry.entityType,
    entry.entityId,
    entry.user.username,
    entry.ipAddress || '',
    entry.correlationId || '',
  ])

  const csv = [headers, ...rows]
    .map(row => row.map(cell => `"${String(cell).replace(/"/g, '""')}"`).join(','))
    .join('\n')

  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')

  const fromStr = dateFrom || 'start'
  const toStr = dateTo || dayjs().format('YYYY-MM-DD')
  link.download = `audit-log-${fromStr}-to-${toStr}.csv`
  link.href = url
  link.click()
  URL.revokeObjectURL(url)
}
```

### Expandable Row Pattern

```tsx
// AuditLogsTable.tsx
const expandedRowRender = (record: AuditLogEntry) => (
  <div style={{ padding: '16px 0' }}>
    <Descriptions column={2} size="small">
      <Descriptions.Item label="Entity ID">
        <Text code>{record.entityId}</Text>
      </Descriptions.Item>
      <Descriptions.Item label="Correlation ID">
        <Text code>{record.correlationId}</Text>
      </Descriptions.Item>
      <Descriptions.Item label="IP Address">
        {record.ipAddress || '—'}
      </Descriptions.Item>
    </Descriptions>
    {record.changes && (
      <ChangesViewer
        before={record.changes.before}
        after={record.changes.after}
        action={record.action}
      />
    )}
  </div>
)

<Table
  expandable={{
    expandedRowRender,
    rowExpandable: () => true,
  }}
/>
```

### Role-based Access Check

```tsx
// AuditPage.tsx
import { useAuth } from '@/shared/hooks/useAuth'
import { Navigate } from 'react-router-dom'
import { message } from 'antd'

export function AuditPage() {
  const { user } = useAuth()

  if (user?.role === 'developer') {
    message.error('Недостаточно прав для просмотра аудит-логов')
    return <Navigate to="/" replace />
  }

  // ... rest of component
}
```

### Testing Strategy

**Unit тесты с Vitest + React Testing Library:**
```typescript
// AuditPage.test.tsx
import { renderWithMockAuth } from '@/test/test-utils'
import { AuditPage } from './AuditPage'

describe('AuditPage', () => {
  it('отображает таблицу для security пользователя', async () => {
    renderWithMockAuth(<AuditPage />, {
      authValue: { user: { role: 'security' } }
    })
    await waitFor(() => {
      expect(screen.getByText(/Audit Logs/i)).toBeInTheDocument()
    })
  })

  it('редиректит developer на главную', async () => {
    const { container } = renderWithMockAuth(<AuditPage />, {
      authValue: { user: { role: 'developer' } }
    })
    // Assert redirect
  })
})
```

### Sidebar уже настроен

```tsx
// Из Sidebar.tsx (line 50) — уже существует:
{
  key: '/audit',
  icon: <AuditOutlined />,
  label: 'Audit Logs',
}
```

### App.tsx placeholder

```tsx
// Текущее состояние (line 56):
<Route path="/audit" element={<div>Audit Logs</div>} />

// Заменить на:
<Route path="/audit" element={<AuditPage />} />
```

### Error Handling

| Сценарий | HTTP Code | Действие UI |
|----------|-----------|-------------|
| Unauthorized | 401 | Redirect to /login |
| Forbidden (developer) | 403 | Redirect to /, toast error |
| Server Error | 500 | Error alert, retry button |
| No Data | 200 empty | Empty state component |

### Dependencies (npm packages)

Все необходимые пакеты уже установлены:
- `antd` — UI components (Table, DatePicker, Select, Tag, Descriptions)
- `@ant-design/icons` — AuditOutlined, DownloadOutlined, ExpandOutlined
- `@tanstack/react-query` — useQuery
- `axios` — HTTP client
- `dayjs` — date formatting
- `react-router-dom` — useSearchParams, Navigate

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 7.5: Audit Log UI]
- [Source: frontend/admin-ui/src/features/routes/components/RoutesTable.tsx]
- [Source: frontend/admin-ui/src/features/approval/components/ApprovalsPage.tsx]
- [Source: backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/controller/AuditController.kt]
- [Source: _bmad-output/implementation-artifacts/7-4-routes-upstream-filter.md]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

N/A — реализация прошла без критических проблем

### Completion Notes List

1. Создана полная структура audit feature с типами, API, hooks, компонентами
2. Реализована таблица аудит-логов с expandable rows для просмотра деталей (AC1, AC3)
3. Панель фильтров с date range picker (presets), user dropdown, entity type, action multi-select (AC2)
4. Debounce 300ms для ВСЕХ фильтров, URL sync через useSearchParams (AC2)
5. Color-coded action badges с русскими лейблами (AC5)
6. CSV export загружает ВСЕ filtered данные (до 10000), с BOM для кириллицы в Excel (AC4)
7. Role-based access: developer редиректится с однократным toast ошибки (AC6)
8. Empty state и skeleton loading (AC7)
9. ChangesViewer обрабатывает все типы actions (created/updated/deleted/approved/rejected/submitted/published)
10. Entity link не показывается для deleted routes
11. 56 unit тестов для всех компонентов — все проходят успешно
12. TypeScript компиляция без ошибок (audit feature)

### File List

**Новые файлы:**
- frontend/admin-ui/src/features/audit/types/audit.types.ts
- frontend/admin-ui/src/features/audit/config/auditConfig.ts
- frontend/admin-ui/src/features/audit/api/auditApi.ts
- frontend/admin-ui/src/features/audit/hooks/useAuditLogs.ts
- frontend/admin-ui/src/features/audit/components/AuditFilterBar.tsx
- frontend/admin-ui/src/features/audit/components/AuditFilterBar.test.tsx
- frontend/admin-ui/src/features/audit/components/AuditLogsTable.tsx
- frontend/admin-ui/src/features/audit/components/AuditLogsTable.test.tsx
- frontend/admin-ui/src/features/audit/components/ChangesViewer.tsx
- frontend/admin-ui/src/features/audit/components/ChangesViewer.test.tsx
- frontend/admin-ui/src/features/audit/components/AuditPage.tsx
- frontend/admin-ui/src/features/audit/components/AuditPage.test.tsx
- frontend/admin-ui/src/features/audit/utils/exportCsv.ts
- frontend/admin-ui/src/features/audit/index.ts

**Модифицируемые файлы:**
- frontend/admin-ui/src/App.tsx — route update, ProtectedRoute для /audit
- _bmad-output/implementation-artifacts/sprint-status.yaml — status update

### Change Log

- 2026-02-20: Реализована Story 7.5 — Audit Log UI с таблицей, фильтрами, expandable rows, CSV export и role-based access
- 2026-02-20: Code Review исправления (6 issues fixed):
  - H1: Action filter изменён на multi-select (AC2) — `mode="multiple"` добавлен
  - H2: CSV экспорт теперь загружает все filtered данные (до 10000), а не только текущую страницу (AC4)
  - H3: message.error для developer role вынесен в useEffect для однократного показа
  - M1: Debounce 300ms применён ко ВСЕМ фильтрам (userId, entityType, dateRange), не только action
  - M2: ChangesViewer обрабатывает approved/rejected/submitted actions
  - M3: Entity link не показывается для deleted routes (избежание 404)
- 2026-02-20: Code Review #2 исправления (5 issues fixed):
  - H1: Timestamp формат исправлен на английскую локаль (AC1: "Feb 11, 2026, 14:30") — убран `dayjs.locale('ru')`
  - H2: Добавлен action 'published' в типы, labels и colors для соответствия backend schema
  - M1: Добавлена runtime валидация action values из URL через parseActionParam()
  - M2: Добавлен тест для CSV export click — проверка вызова fetchAllAuditLogsForExport и downloadAuditCsv
  - M3: ChangesViewer поддерживает 'published' action с заголовком "Опубликованные данные"
