# Story 15.6: Page Header Consistency

Status: done

## Story

As a **user of admin-ui**,
I want **all pages to have consistent header formatting** (icon + English title + description),
so that **the interface looks professional and provides clear context for each section**.

## Acceptance Criteria

1. **AC1: Header Structure** — Все страницы имеют единую структуру заголовка:
   - Иконка из sidebar (24px, color #1890ff)
   - Название на английском (Title level={3}, margin: 0)
   - Обёртка в `<Space>` с `align="center"`

2. **AC2: PageInfoBlock** — Под заголовком каждой страницы добавлен `PageInfoBlock` с:
   - Кратким описанием назначения страницы
   - Списком ключевых features (где применимо)

3. **AC3: Consistency with Reference** — Все страницы соответствуют референсу (IntegrationsPage):
   - Dashboard
   - Routes
   - Rate Limits
   - Users
   - Consumers
   - Approvals
   - Audit Logs
   - Metrics
   - Test

4. **AC4: Sidebar Icons Match** — Иконки в заголовках соответствуют иконкам в sidebar

5. **AC5: English Titles** — Все заголовки на английском языке (заменить русские где есть)

## Tasks / Subtasks

- [x] Task 1: Обновить pageDescriptions.ts (AC: 2)
  - [x] Добавить описания для всех 9 страниц — уже готово в Story 15.4
  - [x] Использовать формат: title, description, features[]

- [x] Task 2: Dashboard page (AC: 1, 2, 4)
  - [x] Добавить DashboardOutlined иконку в заголовок
  - [x] Добавить PageInfoBlock — уже готово в Story 15.4

- [x] Task 3: Routes page (AC: 1, 2, 4)
  - [x] Добавить ApiOutlined иконку в заголовок
  - [x] Добавить PageInfoBlock — уже готово в Story 15.4

- [x] Task 4: Rate Limits page (AC: 1, 2, 4)
  - [x] Добавить SafetyOutlined иконку в заголовок
  - [x] Изменить заголовок "Rate Limit Policies" на "Rate Limits"
  - [x] Добавить PageInfoBlock — уже готово в Story 15.4

- [x] Task 5: Users page (AC: 1, 2, 4)
  - [x] Добавить TeamOutlined иконку в заголовок
  - [x] Добавить PageInfoBlock — уже готово в Story 15.4

- [x] Task 6: Consumers page (AC: 1, 2, 4)
  - [x] Добавить ApiOutlined иконку в заголовок
  - [x] Добавить PageInfoBlock — уже готово в Story 15.4

- [x] Task 7: Approvals page (AC: 1, 2, 4, 5)
  - [x] Добавить CheckCircleOutlined иконку в заголовок
  - [x] Изменить заголовок с "Согласование маршрутов" на "Approvals"
  - [x] Добавить PageInfoBlock — уже готово в Story 15.4

- [x] Task 8: Audit Logs page (AC: 1, 2, 4, 5)
  - [x] Добавить AuditOutlined иконку в заголовок
  - [x] Изменить заголовок с "Аудит-логи" на "Audit Logs"
  - [x] Изменить кнопку "Экспорт CSV" на "Export CSV"
  - [x] Добавить PageInfoBlock — уже готово в Story 15.4

- [x] Task 9: Metrics page (AC: 1, 2, 4)
  - [x] Добавить AreaChartOutlined иконку и главный заголовок "Metrics"
  - [x] Добавить PageInfoBlock — уже готово в Story 15.4

- [x] Task 10: Test page (AC: 1, 2, 4)
  - [x] Унифицировать структуру заголовка с ExperimentOutlined
  - [x] Изменить заголовок "Test Load Generator" на "Test"
  - [x] Добавить PageInfoBlock — уже готово в Story 15.4

## Dev Notes

### Референс (IntegrationsPage.tsx)

```tsx
<Card>
  <div style={{ marginBottom: 24 }}>
    <Space align="center" style={{ width: '100%', justifyContent: 'space-between' }}>
      <Space>
        <ClusterOutlined style={{ fontSize: 24, color: '#1890ff' }} />
        <Title level={3} style={{ margin: 0 }}>
          Integrations Report
        </Title>
      </Space>
      {/* Кнопка справа (если есть) */}
    </Space>
  </div>
  <PageInfoBlock pageKey="integrations" {...PAGE_DESCRIPTIONS.integrations} />
  {/* Контент */}
</Card>
```

### Маппинг страниц → иконок

| Страница | Иконка (Ant Design) | Текущий заголовок | Целевой заголовок |
|----------|---------------------|-------------------|-------------------|
| Dashboard | DashboardOutlined | Dashboard | Dashboard |
| Routes | ApiOutlined | Routes | Routes |
| Rate Limits | SafetyOutlined | Rate Limit Policies | Rate Limits |
| Users | TeamOutlined | Users | Users |
| Consumers | ApiOutlined | Consumers | Consumers |
| Approvals | CheckCircleOutlined | Согласование маршрутов | Approvals |
| Audit Logs | AuditOutlined | Аудит-логи | Audit Logs |
| Metrics | AreaChartOutlined | (нет главного) | Metrics |
| Test | ExperimentOutlined | Test Load Generator | Test |

### Файлы для изменения

```
frontend/admin-ui/src/
├── shared/config/pageDescriptions.ts    # Добавить описания для 9 страниц
├── features/
│   ├── dashboard/components/DashboardPage.tsx
│   ├── routes/components/RoutesPage.tsx
│   ├── rate-limits/components/RateLimitsPage.tsx
│   ├── users/components/UsersPage.tsx
│   ├── consumers/components/ConsumersPage.tsx
│   ├── approvals/components/ApprovalsPage.tsx
│   ├── audit/components/AuditPage.tsx
│   ├── metrics/components/MetricsPage.tsx
│   └── test/components/TestPage.tsx
```

### Предлагаемые описания (для PageInfoBlock)

| Страница | Description | Features |
|----------|-------------|----------|
| Dashboard | Overview of API Gateway system status and key metrics | System health, Active routes, Recent activity |
| Routes | Manage API routes and their upstream configurations | Create, Edit, Delete routes; Version control; Approval workflow |
| Rate Limits | Configure rate limiting policies to protect your APIs | Token bucket algorithm; Per-consumer limits; Burst handling |
| Users | Manage admin panel users and their roles | Role-based access; Password management |
| Consumers | View and manage API consumers (Keycloak users) | Consumer list; Usage statistics |
| Approvals | Review and approve route changes before publishing | Pending changes; Approve/Reject; Change history |
| Audit Logs | Track all changes made to routes and system configuration | Filter by action; User tracking; Date range |
| Metrics | Monitor API traffic and performance metrics | Request rates; Latency percentiles; Error rates |
| Test | Generate test load to verify route configurations | Concurrent requests; Response validation |

### Project Structure Notes

- PageInfoBlock уже существует: `src/shared/components/PageInfoBlock.tsx`
- PAGE_DESCRIPTIONS уже существует: `src/shared/config/pageDescriptions.ts`
- Integrations уже использует этот паттерн (Story 15.4)

### References

- [Source: frontend/admin-ui/src/features/audit/components/IntegrationsPage.tsx] — референс
- [Source: frontend/admin-ui/src/shared/components/PageInfoBlock.tsx] — компонент
- [Source: frontend/admin-ui/src/shared/config/pageDescriptions.ts] — конфиг описаний
- [Source: frontend/admin-ui/src/components/Sidebar.tsx:57-108] — иконки sidebar

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- Все 716 unit-тестов проходят

### Completion Notes List

1. **Task 1 (pageDescriptions.ts):** Уже выполнена в Story 15.4 — все 10 страниц имеют описания
2. **Tasks 2-10 (страницы):** Унифицирована структура заголовков на 9 страницах:
   - Добавлены иконки (24px, color #1890ff) соответствующие sidebar
   - Все заголовки изменены на `Title level={3}` с `margin: 0`
   - Обёртка в `Space` с `align="center"`
   - Русские заголовки заменены на английские (Approvals, Audit Logs)
   - Кнопка "Экспорт CSV" заменена на "Export CSV" на странице Audit Logs
3. **Тесты:** Обновлены 5 тестовых файлов для соответствия новой структуре:
   - DashboardPage.test.tsx — level 2 → level 3
   - RateLimitsPage.test.tsx — "Rate Limit Policies" → "Rate Limits"
   - TestPage.test.tsx — "Test Load Generator" → "Test"
   - AuditPage.test.tsx — "Аудит-логи" → "Audit Logs", "Экспорт CSV" → "Export CSV"

### File List

**Изменённые файлы:**
- frontend/admin-ui/src/features/dashboard/components/DashboardPage.tsx
- frontend/admin-ui/src/features/dashboard/components/DashboardPage.test.tsx
- frontend/admin-ui/src/features/routes/components/RoutesPage.tsx
- frontend/admin-ui/src/features/rate-limits/components/RateLimitsPage.tsx
- frontend/admin-ui/src/features/rate-limits/components/RateLimitsPage.test.tsx
- frontend/admin-ui/src/features/users/components/UsersPage.tsx
- frontend/admin-ui/src/features/consumers/components/ConsumersPage.tsx
- frontend/admin-ui/src/features/approval/components/ApprovalsPage.tsx
- frontend/admin-ui/src/features/approval/components/ApprovalsPage.test.tsx
- frontend/admin-ui/src/features/audit/components/AuditPage.tsx
- frontend/admin-ui/src/features/audit/components/AuditPage.test.tsx
- frontend/admin-ui/src/features/metrics/components/MetricsPage.tsx
- frontend/admin-ui/src/features/test/components/TestPage.tsx
- frontend/admin-ui/src/features/test/components/TestPage.test.tsx

## Senior Developer Review (AI)

### Review Date
2026-03-04

### Reviewer
Claude Opus 4.5 (claude-opus-4-5-20251101)

### Issues Found and Fixed

**HIGH Issues (3):**
1. **ApprovalsPage** — Несоответствие структуры референсу (отсутствовал `justifyContent: 'space-between'` и Card wrapper)
   - Fixed: Добавлен Card wrapper, кнопка Refresh перемещена справа от заголовка
2. **MetricsPage** — Заголовок в отдельном Card (нарушение консистентности)
   - Fixed: Объединены Card заголовка и Card с time range selector, кнопка Grafana перемещена справа
3. **DashboardPage** — Кнопка Logout находилась внизу, а не справа от заголовка
   - Fixed: Кнопка Logout перемещена справа от заголовка с `space-between`

**MEDIUM Issues (4):**
1. **RateLimitsPage** — Нет обёртки в Card → Fixed: Добавлен Card wrapper
2. **UsersPage** — Нет обёртки в Card → Fixed: Добавлен Card wrapper
3. **ConsumersPage** — Нет обёртки в Card → Fixed: Добавлен Card wrapper
4. **ApprovalsPage** — Смешанный язык UI (русский placeholder + английский заголовок) → Fixed: UI элементы переведены на английский

**LOW Issues (2):**
1. **TestPage** — Нет Card wrapper → Fixed: Добавлен Card wrapper для заголовка и PageInfoBlock
2. Неполное тестовое покрытие → Fixed: Обновлены тесты ApprovalsPage.test.tsx

### Tests Updated
- ApprovalsPage.test.tsx — "Обновить" → "Refresh", "Сбросить фильтры" → "Clear filters", placeholder на английском

### Verification
- Все 716 unit-тестов проходят
- Структура всех страниц соответствует референсу (IntegrationsPage)

### Outcome
**APPROVED** — Все HIGH и MEDIUM issues исправлены

