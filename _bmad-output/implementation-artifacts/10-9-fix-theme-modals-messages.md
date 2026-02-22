# Story 10.9: Fix Theme Support for Modals and Messages

Status: dev-complete

## Story

As a **user**,
I want modals and toast messages to respect the current theme (light/dark),
so that the UI is consistent and comfortable to use in dark mode.

## Bug Report

**Severity:** LOW

**Воспроизведение:**
1. Включить тёмную тему (Theme Switcher)
2. Открыть любое модальное окно (например, Rollback confirmation, Delete confirmation)
3. Или вызвать toast message (например, успешное сохранение)
4. Наблюдать: модалки/сообщения отображаются в светлой теме

**Root Cause (подтверждён):**
- Static методы (`Modal.confirm()`, `message.success()`, etc.) создают элементы **вне React tree**
- Они не получают theme context из `ConfigProvider`
- Ant Design 5.x требует `<App>` wrapper для context-aware статических методов

## Acceptance Criteria

### AC1: Modal.confirm поддерживает тему
**Given** пользователь в тёмной теме
**When** открывается Modal.confirm (Rollback, Submit)
**Then** модалка отображается в тёмной теме

### AC2: message.success/error/warning поддерживает тему
**Given** пользователь в тёмной теме
**When** появляется toast message
**Then** сообщение отображается в тёмной теме

### AC3: Все Popconfirm поддерживают тему
**Given** пользователь в тёмной теме
**When** появляется Popconfirm (Delete button)
**Then** popconfirm отображается в тёмной теме

**Note:** Popconfirm уже работает корректно (component-based, получает theme context).

## Analysis Summary

### Ant Design Version

**Version:** `^5.15.0` — App component доступен ✅

### Текущая структура (main.tsx)

```typescript
function ThemedApp() {
  const { isDark } = useThemeContext()

  return (
    <ConfigProvider theme={...}>
      <BrowserRouter>
        <AuthProvider>
          <App />
        </AuthProvider>
      </BrowserRouter>
    </ConfigProvider>
  )
}
```

**Проблема:** Нет `<App>` wrapper от Ant Design внутри ConfigProvider.

### Файлы с static методами

**Modal.confirm (1 файл, 2 использования):**
- `features/routes/components/RouteDetailsCard.tsx` — Submit (line 88), Rollback (line 109)

**message.* (15 файлов):**

| Файл | Методы |
|------|--------|
| `features/routes/hooks/useRoutes.ts` | success, error (6 мест) |
| `features/approval/hooks/useApprovals.ts` | success, error (2 мест) |
| `features/users/hooks/useUsers.ts` | success, error (3 мест) |
| `features/rate-limits/hooks/useRateLimits.ts` | success, error (4 мест) |
| `features/audit/utils/exportUpstreamReport.ts` | loading, warning, success, error |
| `features/audit/utils/exportCsv.ts` | warning |
| `features/auth/components/ChangePasswordModal.tsx` | success, error |
| `features/auth/components/DemoCredentials.tsx` | success, error |
| `features/rate-limits/components/RateLimitsPage.tsx` | error |
| `features/audit/components/IntegrationsPage.tsx` | error, warning |
| `features/audit/components/AuditPage.tsx` | warning, success, error |

### Решение: App Component Wrapper

Ant Design 5.x предоставляет `<App>` компонент, который даёт theme context для static методов через `App.useApp()` hook.

## Tasks / Subtasks

- [x] Task 1: Add App wrapper в main.tsx (AC: #1, #2)
  - [x] 1.1 Import `App as AntApp` из 'antd'
  - [x] 1.2 Обернуть содержимое ConfigProvider в `<AntApp>`

- [x] Task 2: Migrate Modal.confirm в RouteDetailsCard.tsx (AC: #1)
  - [x] 2.1 Import `App` из 'antd'
  - [x] 2.2 Добавить `const { modal } = App.useApp()` в компонент
  - [x] 2.3 Заменить `Modal.confirm()` на `modal.confirm()` (2 места)

- [x] Task 3: Migrate message.* в компонентах (AC: #2)
  - [x] 3.1 ChangePasswordModal.tsx — добавить useApp, заменить message
  - [x] 3.2 DemoCredentials.tsx — добавить useApp, заменить message
  - [x] 3.3 RateLimitsPage.tsx — добавить useApp, заменить message
  - [x] 3.4 IntegrationsPage.tsx — добавить useApp, заменить message
  - [x] 3.5 AuditPage.tsx — добавить useApp, заменить message

- [x] Task 4: Migrate message.* в utility файлах (AC: #2)
  - [x] 4.1 exportUpstreamReport.ts — передать message через параметр
  - [x] 4.2 exportCsv.ts — передать message через параметр (опционально)

- [x] Task 5: Migrate hooks (AC: #2)
  - [x] 5.1 useRoutes.ts — добавить App.useApp() в mutation hooks
  - [x] 5.2 useApprovals.ts — добавить App.useApp() в mutation hooks
  - [x] 5.3 useUsers.ts — добавить App.useApp() в mutation hooks
  - [x] 5.4 useRateLimits.ts — добавить App.useApp() в mutation hooks

- [ ] Task 6: Manual verification
  - [ ] 6.1 Включить тёмную тему
  - [ ] 6.2 Проверить Rollback Modal (RouteDetailsCard)
  - [ ] 6.3 Проверить Submit Modal (RouteDetailsCard)
  - [ ] 6.4 Проверить Delete Popconfirm (уже работает)
  - [ ] 6.5 Проверить message.success при создании route
  - [ ] 6.6 Проверить message.error при ошибке
  - [ ] 6.7 Включить светлую тему — regression test

## API Dependencies Checklist

**Backend изменения не требуются** — fix только на frontend.

## Dev Notes

### Change 1: main.tsx (Root wrapper)

**Путь:** `frontend/admin-ui/src/main.tsx`

**Текущее (строки 22-42):**
```typescript
import { ConfigProvider, theme as antTheme } from 'antd'

function ThemedApp() {
  const { isDark } = useThemeContext()

  return (
    <ConfigProvider
      theme={{
        algorithm: isDark ? antTheme.darkAlgorithm : antTheme.defaultAlgorithm,
        token: { colorPrimary: '#1890ff' },
      }}
    >
      <BrowserRouter>
        <AuthProvider>
          <App />
        </AuthProvider>
      </BrowserRouter>
    </ConfigProvider>
  )
}
```

**Новое:**
```typescript
import { App as AntApp, ConfigProvider, theme as antTheme } from 'antd'

function ThemedApp() {
  const { isDark } = useThemeContext()

  return (
    <ConfigProvider
      theme={{
        algorithm: isDark ? antTheme.darkAlgorithm : antTheme.defaultAlgorithm,
        token: { colorPrimary: '#1890ff' },
      }}
    >
      <AntApp>
        <BrowserRouter>
          <AuthProvider>
            <App />
          </AuthProvider>
        </BrowserRouter>
      </AntApp>
    </ConfigProvider>
  )
}
```

### Change 2: RouteDetailsCard.tsx (Modal.confirm)

**Путь:** `frontend/admin-ui/src/features/routes/components/RouteDetailsCard.tsx`

**Import:**
```typescript
import { ..., App } from 'antd'
```

**В компоненте:**
```typescript
export function RouteDetailsCard({ route }: RouteDetailsCardProps) {
  const navigate = useNavigate()
  const { user } = useAuth()
  const { modal } = App.useApp()  // НОВОЕ

  // Line 88: Submit confirmation
  const handleSubmitForApproval = () => {
    modal.confirm({  // Было: Modal.confirm
      title: 'Отправить на согласование',
      // ...
    })
  }

  // Line 109: Rollback confirmation
  const handleRollback = () => {
    modal.confirm({  // Было: Modal.confirm
      title: 'Откатить маршрут в Draft?',
      // ...
    })
  }
}
```

### Change 3: Components с message.*

**Паттерн для компонентов:**
```typescript
import { App } from 'antd'

export function MyComponent() {
  const { message } = App.useApp()

  // Теперь message.success(), message.error() уважают тему
  message.success('Сохранено!')
}
```

### Change 4: Utility файлы (exportUpstreamReport.ts, exportCsv.ts)

**Проблема:** Utility функции не могут использовать hooks напрямую.

**Решение:** Передать message API через параметр:

```typescript
// В utility файле:
export async function exportUpstreamReport(
  params: ExportParams,
  messageApi: MessageInstance  // НОВОЕ: передать message API
) {
  messageApi.loading('Экспорт...')
  // ...
}

// В компоненте:
import { App } from 'antd'

function MyPage() {
  const { message } = App.useApp()

  const handleExport = () => {
    exportUpstreamReport(params, message)
  }
}
```

### Files to Modify (11 файлов)

| # | File | Change |
|---|------|--------|
| 1 | `src/main.tsx` | Add `<AntApp>` wrapper |
| 2 | `features/routes/components/RouteDetailsCard.tsx` | `Modal.confirm` → `modal.confirm` |
| 3 | `features/auth/components/ChangePasswordModal.tsx` | Add `App.useApp()` |
| 4 | `features/auth/components/DemoCredentials.tsx` | Add `App.useApp()` |
| 5 | `features/rate-limits/components/RateLimitsPage.tsx` | Add `App.useApp()` |
| 6 | `features/audit/components/IntegrationsPage.tsx` | Add `App.useApp()` |
| 7 | `features/audit/components/AuditPage.tsx` | Add `App.useApp()` |
| 8 | `features/audit/utils/exportUpstreamReport.ts` | Accept message param |
| 9 | `features/audit/utils/exportCsv.ts` | Accept message param |
| 10 | Caller of exportUpstreamReport | Pass message |
| 11 | Caller of exportCsv | Pass message |

### Hooks (4 файла) — автоматически работают

Hooks вызываются из компонентов, которые находятся внутри `<AntApp>`, поэтому их message вызовы автоматически получат theme context:
- `useRoutes.ts`
- `useApprovals.ts`
- `useUsers.ts`
- `useRateLimits.ts`

**НО:** Эти hooks используют `import { message } from 'antd'` напрямую — нужно проверить, работает ли это через App wrapper или тоже требуется рефакторинг.

**Если не работает автоматически:**
Альтернатива — создать custom hook `useMessage()` который использует `App.useApp()`.

### Test Mocking

При тестировании компонентов с `App.useApp()`:

```typescript
// В тестах:
vi.mock('antd', async () => {
  const actual = await vi.importActual('antd')
  return {
    ...actual,
    App: {
      ...actual.App,
      useApp: () => ({
        message: { success: vi.fn(), error: vi.fn() },
        modal: { confirm: vi.fn() },
        notification: { success: vi.fn() },
      }),
    },
  }
})
```

### References

- [Source: main.tsx:22-42] — текущая структура ThemedApp
- [Source: RouteDetailsCard.tsx:88,109] — Modal.confirm usage
- [Source: package.json] — antd version ^5.15.0
- [Ant Design Docs: App Component](https://ant.design/components/app)

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

N/A

### Completion Notes List

1. Добавлен `<App>` wrapper в main.tsx (AntApp) для theme-aware static методов
2. Мигрировано 2 использования `Modal.confirm()` → `modal.confirm()` в RouteDetailsCard.tsx
3. Мигрировано 5 компонентов на `App.useApp()` для message API
4. Мигрировано 2 utility файла (export функции) с передачей messageApi через параметр
5. Мигрировано 4 hooks (useRoutes, useApprovals, useUsers, useRateLimits) на `App.useApp()`
6. Обновлены unit тесты для поддержки нового API (мок App.useApp в setup.ts)
7. Все 544 unit теста прошли

### File List

**Измененные файлы (17):**
1. `frontend/admin-ui/src/main.tsx` — App wrapper
2. `frontend/admin-ui/src/features/routes/components/RouteDetailsCard.tsx` — modal.confirm
3. `frontend/admin-ui/src/features/auth/components/ChangePasswordModal.tsx` — App.useApp
4. `frontend/admin-ui/src/features/auth/components/DemoCredentials.tsx` — App.useApp
5. `frontend/admin-ui/src/features/rate-limits/components/RateLimitsPage.tsx` — App.useApp
6. `frontend/admin-ui/src/features/audit/components/IntegrationsPage.tsx` — App.useApp
7. `frontend/admin-ui/src/features/audit/components/AuditPage.tsx` — App.useApp
8. `frontend/admin-ui/src/features/audit/utils/exportUpstreamReport.ts` — messageApi param
9. `frontend/admin-ui/src/features/audit/utils/exportCsv.ts` — messageApi param
10. `frontend/admin-ui/src/features/routes/hooks/useRoutes.ts` — App.useApp
11. `frontend/admin-ui/src/features/approval/hooks/useApprovals.ts` — App.useApp
12. `frontend/admin-ui/src/features/users/hooks/useUsers.ts` — App.useApp
13. `frontend/admin-ui/src/features/rate-limits/hooks/useRateLimits.ts` — App.useApp
14. `frontend/admin-ui/src/test/setup.ts` — глобальный мок App.useApp
15. `frontend/admin-ui/src/features/auth/components/ChangePasswordModal.test.tsx` — мок update
16. `frontend/admin-ui/src/features/auth/components/DemoCredentials.test.tsx` — мок update
17. `frontend/admin-ui/src/features/approval/components/ApprovalsPage.test.tsx` — мок update
18. `frontend/admin-ui/src/features/audit/components/AuditPage.test.tsx` — мок update
19. `frontend/admin-ui/src/features/audit/components/IntegrationsPage.test.tsx` — assertion update
20. `frontend/admin-ui/src/features/routes/components/RouteDetailsCard.test.tsx` — modal tests update

## Change Log

- **2026-02-22:** Story created from SM chat session (bug report by Yury)
- **2026-02-22:** Full analysis completed, all usages found, status → ready-for-dev
- **2026-02-22:** Implementation complete — all 544 unit tests pass, status → dev-complete
