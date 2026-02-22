# Story 10.9: Fix Theme Support for Modals and Messages

Status: draft

## Story

As a **user**,
I want modals and toast messages to respect the current theme (light/dark),
so that the UI is consistent and comfortable to use in dark mode.

## Bug Report

**Воспроизведение:**
1. Включить тёмную тему (Theme Switcher)
2. Открыть любое модальное окно (например, Rollback confirmation, Delete confirmation)
3. Или вызвать toast message (например, успешное сохранение)
4. Наблюдать: модалки/сообщения отображаются в светлой теме

**Ожидаемое поведение:** Модалки и сообщения должны использовать текущую тему.

## Acceptance Criteria

### AC1: Modal.confirm поддерживает тему
**Given** пользователь в тёмной теме
**When** открывается Modal.confirm (Rollback, Delete, etc.)
**Then** модалка отображается в тёмной теме

### AC2: message.success/error/warning поддерживает тему
**Given** пользователь в тёмной теме
**When** появляется toast message
**Then** сообщение отображается в тёмной теме

### AC3: Все Popconfirm поддерживают тему
**Given** пользователь в тёмной теме
**When** появляется Popconfirm (Delete button, etc.)
**Then** popconfirm отображается в тёмной теме

## Analysis Summary

### Ant Design Theme Context

Ant Design использует `ConfigProvider` для темизации. Проблема: `Modal.confirm()`, `message.success()` и подобные **статические методы** создают элементы вне React tree, поэтому не получают theme context.

### Решение (Ant Design 5.x)

**App Component wrapper:**

```typescript
import { App, ConfigProvider } from 'antd'

// В корне приложения
<ConfigProvider theme={currentTheme}>
  <App>
    <Router>...</Router>
  </App>
</ConfigProvider>

// В компонентах использовать хуки вместо статических методов
import { App } from 'antd'

function MyComponent() {
  const { modal, message, notification } = App.useApp()

  // Вместо Modal.confirm()
  modal.confirm({ title: '...' })

  // Вместо message.success()
  message.success('...')
}
```

### Текущая структура (предположительно)

Нужно проверить:
1. Где находится `ConfigProvider`
2. Есть ли `App` wrapper
3. Какие компоненты используют статические методы

## Tasks / Subtasks

- [ ] Task 1: Research current implementation
  - [ ] 1.1 Найти ConfigProvider в проекте
  - [ ] 1.2 Найти все использования Modal.confirm, message.*, notification.*
  - [ ] 1.3 Проверить версию Ant Design (App component доступен в 5.x)

- [ ] Task 2: Add App wrapper (AC: #1, #2, #3)
  - [ ] 2.1 Обернуть приложение в `<App>` компонент внутри ConfigProvider
  - [ ] 2.2 Экспортировать useApp hook или создать wrapper

- [ ] Task 3: Migrate static methods to hooks (AC: #1, #2, #3)
  - [ ] 3.1 Заменить `Modal.confirm()` на `modal.confirm()` через useApp
  - [ ] 3.2 Заменить `message.success/error()` на hook версии
  - [ ] 3.3 Проверить Popconfirm (обычно работает через ConfigProvider)

- [ ] Task 4: Manual verification
  - [ ] 4.1 Проверить Rollback modal в тёмной теме
  - [ ] 4.2 Проверить Delete confirmation в тёмной теме
  - [ ] 4.3 Проверить toast messages в тёмной теме
  - [ ] 4.4 Проверить все Popconfirm в тёмной теме

## API Dependencies Checklist

**Backend изменения не требуются** — fix только на frontend.

## Dev Notes

### Поиск использований статических методов

```bash
# В frontend директории
grep -r "Modal.confirm" src/
grep -r "message.success\|message.error\|message.warning" src/
grep -r "notification." src/
```

### Ant Design App Component

Документация: https://ant.design/components/app

```typescript
// App component предоставляет контекст для статических методов
import { App } from 'antd'

const { message, modal, notification } = App.useApp()
```

### Альтернативный подход (если App не подходит)

Можно использовать `message.useMessage()`, `Modal.useModal()`, `notification.useNotification()` хуки:

```typescript
const [messageApi, contextHolder] = message.useMessage()

return (
  <>
    {contextHolder}
    <Button onClick={() => messageApi.success('Saved!')}>Save</Button>
  </>
)
```

Но App component — более чистое решение.

### Files to investigate

- `frontend/admin-ui/src/main.tsx` или `App.tsx` — ConfigProvider
- `frontend/admin-ui/src/features/routes/components/RouteDetailsCard.tsx` — Modal.confirm для Rollback
- `frontend/admin-ui/src/features/routes/hooks/useRoutes.ts` — message.success
- Все компоненты с Popconfirm

## Change Log

- **2026-02-22:** Hotfix story created from SM chat session (bug report by Yury)
