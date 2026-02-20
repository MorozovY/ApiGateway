# Story 7.6: Route History & Upstream Report UI

Status: review

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **Security Specialist**,
I want to see route history timeline and upstream reports,
so that I can conduct thorough audits and understand integrations (FR23, FR24).

## Acceptance Criteria

**AC1 — Timeline View на странице деталей маршрута:**

**Given** user is viewing route details page (любая роль: developer, security, admin)
**When** "История" tab is selected
**Then** timeline view displays all changes:
- Vertical timeline с dots для каждого события
- Каждое событие показывает: action (badge), user, timestamp
- Expandable для просмотра change details
- Most recent события наверху
**And** timeline загружается из GET /api/v1/routes/{id}/history
**And** loading skeleton показывается во время загрузки

**AC2 — Diff View для Updated Actions:**

**Given** timeline entry for "updated" action
**When** expanded
**Then** diff view shows:
- Changed fields highlighted
- Before value (red/strikethrough или помеченное как "Было:")
- After value (green или помеченное как "Стало:")
**And** только изменённые поля показываются (не весь объект)
**And** для created/deleted/approved/rejected показываются соответствующие данные без diff

**AC3 — Integrations Report Page:**

**Given** user navigates to `/audit/integrations`
**When** the page loads
**Then** upstream services report is displayed:
- Table of unique upstream hosts
- Route count per upstream (колонка "Маршрутов")
- Search/filter по host name
**And** данные загружаются из GET /api/v1/routes/upstreams
**And** default sort by routeCount descending

**AC4 — Click-through на upstream:**

**Given** user clicks on an upstream host row
**When** action is triggered
**Then** переход на /routes?upstream={host} с prefilled фильтром
**And** RoutesTable отображает только маршруты с этим upstream
**And** active filter показан как chip "Upstream: {host}"
**And** shows who created each route and when

**AC5 — Export Upstream Report:**

**Given** export functionality on integrations report
**When** "Export Report" is clicked
**Then** generates CSV report with:
- Upstream service URL
- All routes accessing it (path)
- Route owners (createdBy username)
- Current status (draft/pending/published/rejected)
- Last modified date
**And** filename: `upstream-report-YYYY-MM-DD.csv`

**AC6 — Role-based Access:**

**Given** user with developer role attempts to access `/audit/integrations`
**When** page loads
**Then** user is redirected to home page
**And** error toast: "Недостаточно прав для просмотра отчёта по интеграциям"

**Given** user with security or admin role
**When** accessing `/audit/integrations`
**Then** page loads successfully

**AC7 — Navigation и Sidebar:**

**Given** user is on any page
**When** sidebar is displayed
**Then** Audit section contains два отдельных пункта (flat menu, без submenu):
- "Audit Logs" → /audit (AuditOutlined icon)
- "Integrations" → /audit/integrations (ApiOutlined icon)
**And** активный пункт меню подсвечен

**AC8 — Collapsible Sidebar:**

**Given** user clicks collapse button на sidebar
**When** sidebar collapsed
**Then** sidebar сворачивается до иконок (без текста labels)
**And** collapsed state сохраняется в localStorage
**And** tooltip показывает label при hover на иконку
**And** expand button возвращает полный вид

**AC9 — Empty States:**

**Given** no upstreams exist
**When** integrations page loads
**Then** empty state: "Нет данных о внешних сервисах"
**And** suggestion: "Создайте маршруты с upstream URL для отображения интеграций"

**Given** route has no history (edge case)
**When** history tab loads
**Then** shows: "История изменений отсутствует"

## Tasks / Subtasks

- [x] Task 1: Создать типы для Route History и Upstream Report (AC1, AC3)
  - [x] RouteHistoryEntry: timestamp, action, user (id, username), changes (before/after)
  - [x] RouteHistoryResponse: routeId, currentPath, history[]
  - [x] UpstreamSummary: host, routeCount
  - [x] UpstreamsResponse: upstreams[]
  - [x] Добавить в existing audit.types.ts

- [x] Task 2: Создать API клиенты (AC1, AC3, AC4)
  - [x] Добавить в routesApi.ts: fetchRouteHistory(routeId): Promise<RouteHistoryResponse>
  - [x] Создать api/upstreamsApi.ts: fetchUpstreams(): Promise<UpstreamsResponse>
  - [x] Обработка ошибок RFC 7807

- [x] Task 3: Создать React Query hooks (AC1, AC3)
  - [x] useRouteHistory(routeId) — enabled когда routeId defined
  - [x] useUpstreams() — для integrations page
  - [x] Query keys: ['routes', routeId, 'history'], ['upstreams']

- [x] Task 4: Создать компонент RouteHistoryTimeline (AC1, AC2)
  - [x] Использовать Ant Design Timeline component
  - [x] Вертикальный timeline с dots и labels
  - [x] Action badges с цветами из auditConfig.ts
  - [x] Expandable items (Collapse component внутри Timeline.Item)
  - [x] Интеграция ChangesViewer из Story 7.5

- [x] Task 5: Обновить RouteDetailsPage с Tabs (AC1, AC2)
  - [x] Добавить Ant Design Tabs component
  - [x] Tab 1: "Детали" — существующий RouteDetailsCard
  - [x] Tab 2: "История" — RouteHistoryTimeline
  - [x] History tab виден ВСЕМ ролям (readonly информация)
  - [x] Tabs key sync с URL hash (#details, #history)

- [x] Task 6: Расширить RoutesTable для upstream filter (AC4)
  - [x] Добавить `upstream` в RouteListParams type (route.types.ts)
  - [x] Обновить params extraction в RoutesTable: `upstream: searchParams.get('upstream') || undefined`
  - [x] Добавить upstream в useRoutes hook params
  - [x] Добавить chip для active upstream filter с кнопкой очистки
  - [x] Передавать upstream в backend API call

- [x] Task 7: Создать компонент UpstreamsTable (AC3, AC4)
  - [x] Table с колонками: Upstream Host, Route Count, Actions (View)
  - [x] Click handler: navigate(`/routes?upstream=${encodeURIComponent(host)}`)
  - [x] Sorting по routeCount (default DESC)
  - [x] Search input для фильтрации по host name (frontend filter)

- [x] Task 8: Создать IntegrationsPage (AC3, AC4, AC5, AC6, AC9)
  - [x] Layout: PageHeader + UpstreamsTable
  - [x] Export button с CSV download (AC5)
  - [x] Role check: redirect если не security/admin (AC6)
  - [x] Empty state (AC9)

- [x] Task 9: Реализовать Upstream Report Export (AC5)
  - [x] exportUpstreamReport(upstreams) функция
  - [x] Для каждого upstream — fetch routes с ?upstream=host
  - [x] CSV format с BOM для Excel кириллицы
  - [x] Filename: `upstream-report-YYYY-MM-DD.csv`

- [x] Task 10: Реализовать Collapsible Sidebar (AC8)
  - [x] Добавить state: collapsed (boolean)
  - [x] Сохранять в localStorage: 'sidebar-collapsed'
  - [x] Ant Design Sider: collapsible, collapsed, onCollapse props
  - [x] Menu: inlineCollapsed prop
  - [x] Collapse trigger button внизу sidebar

- [x] Task 11: Обновить Sidebar navigation (AC7)
  - [x] Добавить пункт "Integrations" с key="/audit/integrations"
  - [x] Использовать ClusterOutlined icon для Integrations
  - [x] НЕ использовать submenu (flat menu structure)
  - [x] Убедиться что selectedKeys работает для nested paths

- [x] Task 12: Обновить App.tsx routing (AC6, AC7)
  - [x] Добавить route: `/audit/integrations` → IntegrationsPage
  - [x] ProtectedRoute с requiredRole: 'security'

- [x] Task 13: Unit тесты (AC1-AC9)
  - [x] RouteHistoryTimeline.test.tsx
  - [x] UpstreamsTable.test.tsx
  - [x] IntegrationsPage.test.tsx
  - [x] RoutesTable upstream filter test (через существующий функционал)
  - [x] Sidebar collapsed state test
  - [x] Mock API responses
  - [x] Test role-based redirect
  - [x] Test export functionality

## Dev Notes

### Зависимости от предыдущих stories

**Из Story 7.3 (DONE):**
- Backend API: GET /api/v1/routes/{id}/history
- Response format с chronological history entries

**Из Story 7.4 (DONE):**
- Backend API: GET /api/v1/routes/upstreams
- Response format: { upstreams: [{ host, routeCount }] }
- Backend API: GET /api/v1/routes?upstream=host (partial match filter)

**Из Story 7.5 (DONE):**
- ChangesViewer component — ПЕРЕИСПОЛЬЗОВАТЬ для diff display
- auditConfig.ts — ПЕРЕИСПОЛЬЗОВАТЬ action colors/labels
- AUDIT_ACTION_COLORS, AUDIT_ACTION_LABELS константы
- Role-based access pattern (useAuth + Navigate)

### Backend API Contracts

**GET /api/v1/routes/{id}/history:**
```typescript
interface RouteHistoryResponse {
  routeId: string
  currentPath: string
  history: RouteHistoryEntry[]
}

interface RouteHistoryEntry {
  timestamp: string          // ISO 8601
  action: AuditAction        // 'created' | 'updated' | 'approved' | etc.
  user: { id: string; username: string }
  changes: {
    before?: Record<string, unknown>
    after?: Record<string, unknown>
  } | null
}
```

**GET /api/v1/routes/upstreams:**
```typescript
interface UpstreamsResponse {
  upstreams: UpstreamSummary[]
}

interface UpstreamSummary {
  host: string      // "order-service:8080" (без схемы)
  routeCount: number
}
```

**GET /api/v1/routes?upstream=order-service:**
- Standard PagedResponse<RouteDto>
- ILIKE фильтр (case-insensitive partial match)

### Критические паттерны для реализации

**1. RoutesTable upstream filter (Task 6):**
```typescript
// route.types.ts — добавить
interface RouteListParams {
  // existing...
  upstream?: string  // NEW
}

// RoutesTable.tsx — добавить в params extraction
const params: RouteListParams = useMemo(() => ({
  // existing...
  upstream: searchParams.get('upstream') || undefined,
}), [searchParams])

// Добавить chip для upstream filter
{params.upstream && (
  <Tag
    closable
    onClose={() => updateParams({ upstream: undefined })}
  >
    Upstream: {params.upstream}
  </Tag>
)}
```

**2. Collapsible Sidebar (Task 10):**
```typescript
// Sidebar.tsx
import { useState, useEffect } from 'react'

const SIDEBAR_COLLAPSED_KEY = 'sidebar-collapsed'

function Sidebar() {
  const [collapsed, setCollapsed] = useState(() => {
    return localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === 'true'
  })

  const handleCollapse = (value: boolean) => {
    setCollapsed(value)
    localStorage.setItem(SIDEBAR_COLLAPSED_KEY, String(value))
  }

  return (
    <Sider
      theme="light"
      width={220}
      collapsedWidth={80}
      collapsible
      collapsed={collapsed}
      onCollapse={handleCollapse}
      trigger={null}  // Custom trigger
    >
      {/* Logo */}
      <div className="logo">
        <SafetyOutlined style={{ fontSize: 24 }} />
        {!collapsed && <span>API Gateway</span>}
      </div>

      <Menu
        mode="inline"
        inlineCollapsed={collapsed}
        selectedKeys={[location.pathname]}
        items={menuItems}
        onClick={({ key }) => navigate(key)}
      />

      {/* Custom collapse trigger */}
      <Button
        type="text"
        icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
        onClick={() => handleCollapse(!collapsed)}
        style={{ position: 'absolute', bottom: 16, left: collapsed ? 24 : 80 }}
      />
    </Sider>
  )
}
```

**3. RouteDetailsPage Tabs (Task 5):**
```typescript
// RouteDetailsPage.tsx
import { Tabs } from 'antd'
import { useLocation, useNavigate } from 'react-router-dom'
import { RouteHistoryTimeline } from '@features/audit'

export function RouteDetailsPage() {
  const { id } = useParams<{ id: string }>()
  const location = useLocation()
  const navigate = useNavigate()
  const { data: route, isLoading, error } = useRoute(id)

  // Sync tab with URL hash
  const activeTab = location.hash === '#history' ? 'history' : 'details'

  const handleTabChange = (key: string) => {
    navigate(`${location.pathname}#${key}`, { replace: true })
  }

  if (isLoading) return <Spin />
  if (error || !route) return <Result status="404" />

  return (
    <Tabs
      activeKey={activeTab}
      onChange={handleTabChange}
      items={[
        {
          key: 'details',
          label: 'Детали',
          children: <RouteDetailsCard route={route} />,
        },
        {
          key: 'history',
          label: 'История',
          children: <RouteHistoryTimeline routeId={route.id} />,
        },
      ]}
    />
  )
}
```

**4. Timeline с expandable items (Task 4):**
```typescript
// RouteHistoryTimeline.tsx
import { Timeline, Card, Tag, Collapse, Spin, Empty } from 'antd'
import { ClockCircleOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { useRouteHistory } from '../hooks/useRouteHistory'
import { ChangesViewer } from './ChangesViewer'
import { AUDIT_ACTION_COLORS, AUDIT_ACTION_LABELS } from '../config/auditConfig'

interface Props {
  routeId: string
}

export function RouteHistoryTimeline({ routeId }: Props) {
  const { data, isLoading, error } = useRouteHistory(routeId)

  if (isLoading) return <Spin />
  if (error) return <Alert type="error" message="Ошибка загрузки истории" />
  if (!data?.history?.length) {
    return <Empty description="История изменений отсутствует" />
  }

  return (
    <Timeline
      mode="left"
      items={data.history.map((entry, idx) => ({
        key: idx,
        color: AUDIT_ACTION_COLORS[entry.action] || 'gray',
        dot: <ClockCircleOutlined />,
        label: dayjs(entry.timestamp).format('DD MMM YYYY, HH:mm'),
        children: (
          <Card size="small" style={{ marginBottom: 8 }}>
            <div style={{ marginBottom: 8 }}>
              <Tag color={AUDIT_ACTION_COLORS[entry.action]}>
                {AUDIT_ACTION_LABELS[entry.action]}
              </Tag>
              <span style={{ marginLeft: 8 }}>
                {entry.user.username}
              </span>
            </div>
            {entry.changes && (
              <Collapse ghost size="small">
                <Collapse.Panel header="Показать изменения" key="changes">
                  <ChangesViewer
                    before={entry.changes.before}
                    after={entry.changes.after}
                    action={entry.action}
                  />
                </Collapse.Panel>
              </Collapse>
            )}
          </Card>
        ),
      }))}
    />
  )
}
```

### Sidebar Menu Items Update (Task 11)

```typescript
// Sidebar.tsx — обновить baseMenuItems
const baseMenuItems: ItemType[] = [
  { key: '/dashboard', icon: <DashboardOutlined />, label: 'Dashboard' },
  { key: '/routes', icon: <ApiOutlined />, label: 'Routes' },
  { key: '/rate-limits', icon: <SafetyOutlined />, label: 'Rate Limits' },
  { key: '/approvals', icon: <CheckCircleOutlined />, label: 'Approvals' },
  { key: '/metrics', icon: <AreaChartOutlined />, label: 'Metrics' },
  { key: '/audit', icon: <AuditOutlined />, label: 'Audit Logs' },
  { key: '/audit/integrations', icon: <ClusterOutlined />, label: 'Integrations' },  // NEW
]

// Import ClusterOutlined or use другую иконку:
import { ClusterOutlined } from '@ant-design/icons'
```

### UI Components структура

```
frontend/admin-ui/src/features/audit/
├── components/
│   ├── ... (existing from 7.5)
│   ├── RouteHistoryTimeline.tsx      # NEW
│   ├── RouteHistoryTimeline.test.tsx
│   ├── UpstreamsTable.tsx            # NEW
│   ├── UpstreamsTable.test.tsx
│   ├── IntegrationsPage.tsx          # NEW
│   └── IntegrationsPage.test.tsx
├── api/
│   ├── auditApi.ts                   # existing
│   └── upstreamsApi.ts               # NEW
├── hooks/
│   ├── useAuditLogs.ts               # existing
│   ├── useRouteHistory.ts            # NEW
│   └── useUpstreams.ts               # NEW
├── types/
│   └── audit.types.ts                # EXTEND
└── utils/
    ├── exportCsv.ts                  # existing
    └── exportUpstreamReport.ts       # NEW
```

### File Structure Summary

**Новые файлы:**
- frontend/admin-ui/src/features/audit/components/RouteHistoryTimeline.tsx
- frontend/admin-ui/src/features/audit/components/RouteHistoryTimeline.test.tsx
- frontend/admin-ui/src/features/audit/components/UpstreamsTable.tsx
- frontend/admin-ui/src/features/audit/components/UpstreamsTable.test.tsx
- frontend/admin-ui/src/features/audit/components/IntegrationsPage.tsx
- frontend/admin-ui/src/features/audit/components/IntegrationsPage.test.tsx
- frontend/admin-ui/src/features/audit/hooks/useRouteHistory.ts
- frontend/admin-ui/src/features/audit/hooks/useUpstreams.ts
- frontend/admin-ui/src/features/audit/api/upstreamsApi.ts
- frontend/admin-ui/src/features/audit/utils/exportUpstreamReport.ts

**Модифицируемые файлы:**
- frontend/admin-ui/src/features/audit/types/audit.types.ts — add RouteHistory types
- frontend/admin-ui/src/features/audit/index.ts — export new components
- frontend/admin-ui/src/features/routes/types/route.types.ts — add upstream to RouteListParams
- frontend/admin-ui/src/features/routes/components/RouteDetailsPage.tsx — add Tabs + History
- frontend/admin-ui/src/features/routes/components/RoutesTable.tsx — add upstream filter support
- frontend/admin-ui/src/layouts/Sidebar.tsx — add Integrations menu item + collapsible
- frontend/admin-ui/src/App.tsx — add /audit/integrations route

### Error Handling

| Сценарий | HTTP Code | Действие UI |
|----------|-----------|-------------|
| Route not found | 404 | "Маршрут не найден" message |
| History API error | 500 | Alert в History tab, retry button |
| Forbidden (developer on /audit/integrations) | 403 | Redirect to /, toast error |
| No history | 200 empty | "История изменений отсутствует" |
| No upstreams | 200 empty | Empty state с suggestion |

### Testing Strategy

**Mock данные для тестов:**
```typescript
const mockRouteHistory: RouteHistoryResponse = {
  routeId: 'route-1',
  currentPath: '/api/orders',
  history: [
    {
      timestamp: '2026-02-11T11:00:00Z',
      action: 'approved',
      user: { id: '2', username: 'dmitry' },
      changes: null
    },
    {
      timestamp: '2026-02-11T10:05:00Z',
      action: 'updated',
      user: { id: '1', username: 'maria' },
      changes: {
        before: { upstreamUrl: 'http://v1:8080' },
        after: { upstreamUrl: 'http://v2:8080' }
      }
    },
    {
      timestamp: '2026-02-11T10:00:00Z',
      action: 'created',
      user: { id: '1', username: 'maria' },
      changes: { after: { path: '/api/orders' } }
    },
  ]
}

const mockUpstreams: UpstreamsResponse = {
  upstreams: [
    { host: 'user-service:8080', routeCount: 12 },
    { host: 'order-service:8080', routeCount: 5 },
    { host: 'payment-service:8080', routeCount: 3 },
  ]
}
```

### References

- [Source: _bmad-output/planning-artifacts/epics.md#Story 7.6: Route History & Upstream Report UI]
- [Source: _bmad-output/planning-artifacts/architecture.md#Frontend Architecture]
- [Source: _bmad-output/implementation-artifacts/7-5-audit-log-ui.md#ChangesViewer component]
- [Source: _bmad-output/implementation-artifacts/7-4-routes-upstream-filter.md#Upstreams API]
- [Source: frontend/admin-ui/src/features/routes/components/RouteDetailsPage.tsx]
- [Source: frontend/admin-ui/src/features/routes/components/RoutesTable.tsx]
- [Source: frontend/admin-ui/src/layouts/Sidebar.tsx]

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

N/A

### Completion Notes List

- Task 1-13: Все выполнены
- AC1: Timeline View реализован с RouteHistoryTimeline компонентом
- AC2: Diff View реализован через интеграцию ChangesViewer
- AC3: Integrations Report Page реализована с UpstreamsTable
- AC4: Click-through на upstream реализован с navigate и chip фильтром
- AC5: Export Upstream Report реализован с CSV форматом и BOM
- AC6: Role-based Access реализован через ProtectedRoute и проверку в компонентах
- AC7: Navigation обновлена с flat menu структурой
- AC8: Collapsible Sidebar реализован с localStorage persistence
- AC9: Empty states реализованы для обоих компонентов
- Все 27 unit тестов проходят
- Исправлена существующая ошибка в AuditPage.tsx с conditional hooks

### File List

**Новые файлы:**
- frontend/admin-ui/src/features/audit/api/upstreamsApi.ts
- frontend/admin-ui/src/features/audit/hooks/useRouteHistory.ts
- frontend/admin-ui/src/features/audit/hooks/useUpstreams.ts
- frontend/admin-ui/src/features/audit/components/RouteHistoryTimeline.tsx
- frontend/admin-ui/src/features/audit/components/RouteHistoryTimeline.test.tsx
- frontend/admin-ui/src/features/audit/components/UpstreamsTable.tsx
- frontend/admin-ui/src/features/audit/components/UpstreamsTable.test.tsx
- frontend/admin-ui/src/features/audit/components/IntegrationsPage.tsx
- frontend/admin-ui/src/features/audit/components/IntegrationsPage.test.tsx
- frontend/admin-ui/src/features/audit/utils/exportUpstreamReport.ts
- frontend/admin-ui/src/layouts/Sidebar.test.tsx

**Модифицированные файлы:**
- frontend/admin-ui/src/features/audit/types/audit.types.ts (добавлены Route History и Upstream типы)
- frontend/admin-ui/src/features/audit/index.ts (экспорт новых компонентов)
- frontend/admin-ui/src/features/routes/api/routesApi.ts (fetchRouteHistory функция)
- frontend/admin-ui/src/features/routes/types/route.types.ts (upstream в RouteListParams)
- frontend/admin-ui/src/features/routes/components/RouteDetailsPage.tsx (Tabs с History)
- frontend/admin-ui/src/features/routes/components/RoutesTable.tsx (upstream filter)
- frontend/admin-ui/src/layouts/Sidebar.tsx (Collapsible + Integrations menu)
- frontend/admin-ui/src/App.tsx (IntegrationsPage route)
- frontend/admin-ui/src/features/audit/components/AuditPage.tsx (исправлены conditional hooks)

## Change Log

| Date | Change | Author |
|------|--------|--------|
| 2026-02-20 | Story 7.6 implementation complete: Route History Timeline, Integrations Report, Collapsible Sidebar | Claude Opus 4.5 |
