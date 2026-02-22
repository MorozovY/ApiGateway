# Story 9.3: Role-Based Sidebar Visibility

Status: done

## Story

As a **User**,
I want to see only menu items I have access to,
so that the interface is not cluttered with unavailable options.

## Acceptance Criteria

**AC1 — Developer видит только доступные пункты меню:**

**Given** пользователь с ролью Developer залогинен
**When** отображается sidebar
**Then** видны только пункты:
- Dashboard
- Routes
- Metrics

**AC2 — Security видит расширенный набор меню:**

**Given** пользователь с ролью Security залогинен
**When** отображается sidebar
**Then** видны пункты:
- Dashboard
- Routes
- Approvals (с Badge для pending count)
- Audit
- Integrations
- Metrics

**AC3 — Admin видит все пункты меню:**

**Given** пользователь с ролью Admin залогинен
**When** отображается sidebar
**Then** видны все пункты:
- Dashboard
- Users
- Routes
- Rate Limits
- Approvals (с Badge для pending count)
- Audit
- Integrations
- Metrics
- Test

**AC4 — Скрытые пункты недоступны по URL:**

**Given** Developer или Security пытается перейти на /test, /users или /rate-limits
**When** URL вводится напрямую
**Then** пользователь перенаправляется на /dashboard или видит "Access Denied"

## Tasks / Subtasks

- [x] Task 1: Рефакторинг Sidebar.tsx для role-based filtering (AC1, AC2, AC3)
  - [x] Subtask 1.1: Определить маппинг роль → разрешённые пункты меню
  - [x] Subtask 1.2: Фильтровать baseMenuItems по роли пользователя
  - [x] Subtask 1.3: Сохранить логику Badge для Approvals (security/admin)
  - [x] Subtask 1.4: Сохранить логику Users только для admin

- [x] Task 2: Защита роутов на уровне React Router (AC4)
  - [x] Subtask 2.1: Проверить существующий ProtectedRoute компонент
  - [x] Subtask 2.2: Добавить role-based проверку в router
  - [x] Subtask 2.3: Перенаправлять на /dashboard при недостаточных правах

- [x] Task 3: Обновить тесты
  - [x] Subtask 3.1: Добавить тесты для Sidebar с разными ролями
  - [x] Subtask 3.2: Добавить тесты для route protection
  - [x] Subtask 3.3: Запустить регрессию

## API Dependencies Checklist

**Эта story НЕ требует backend API изменений.**

Вся логика реализуется на frontend — фильтрация меню по `user.role` из AuthContext.

## Dev Notes

### Текущее состояние Sidebar.tsx

**Анализ frontend/admin-ui/src/layouts/Sidebar.tsx:**

```typescript
// Текущая логика (строки 107-140):
// - Developer: всё кроме Integrations
// - Security: всё включая Integrations
// - Admin: всё + Users

// Проблема: Developer видит Rate Limits, Approvals, Audit — это не соответствует AC
```

### Требуемое изменение

**Создать маппинг ролей к пунктам меню:**

```typescript
const ROLE_MENU_ACCESS: Record<User['role'], string[]> = {
  developer: ['/dashboard', '/routes', '/metrics'],
  security: ['/dashboard', '/routes', '/approvals', '/audit', '/audit/integrations', '/metrics'],
  admin: ['/dashboard', '/users', '/routes', '/rate-limits', '/approvals', '/audit', '/audit/integrations', '/metrics', '/test'],
}
```

**Изменить useMemo для menuItems:**

```typescript
const menuItems = useMemo(() => {
  const allowedKeys = user?.role ? ROLE_MENU_ACCESS[user.role] : []

  let items: ItemType[] = baseMenuItems
    .filter((item) => {
      if (item && 'key' in item) {
        return allowedKeys.includes(item.key as string)
      }
      return false
    })
    .map((item) => {
      // Badge для Approvals
      if (item && 'key' in item && item.key === '/approvals' && pendingCount > 0) {
        return {
          ...item,
          label: (
            <Badge count={pendingCount} offset={[8, 0]} size="small">
              Approvals
            </Badge>
          ),
        }
      }
      return item
    })

  // Users только для admin (уже в ROLE_MENU_ACCESS)
  if (user?.role === 'admin') {
    items.splice(1, 0, usersMenuItem)
  }

  return items
}, [user?.role, pendingCount])
```

### Route Protection

**Проверить App.tsx или router конфигурацию:**

Нужно добавить проверку роли на уровне React Router:

```typescript
// Пример RequireRole компонента:
function RequireRole({ roles, children }: { roles: User['role'][], children: React.ReactNode }) {
  const { user } = useAuth()

  if (!user || !roles.includes(user.role)) {
    return <Navigate to="/dashboard" replace />
  }

  return <>{children}</>
}

// Использование в router:
<Route path="/users" element={
  <RequireRole roles={['admin']}>
    <UsersPage />
  </RequireRole>
} />
<Route path="/test" element={
  <RequireRole roles={['admin']}>
    <TestPage />
  </RequireRole>
} />
<Route path="/rate-limits" element={
  <RequireRole roles={['admin']}>
    <RateLimitsPage />
  </RequireRole>
} />
```

### Порядок пунктов меню по ролям

| Роль | Пункты меню (в порядке отображения) |
|------|-------------------------------------|
| Developer | Dashboard, Routes, Metrics |
| Security | Dashboard, Routes, Approvals, Audit, Integrations, Metrics |
| Admin | Dashboard, Users, Routes, Rate Limits, Approvals, Audit, Integrations, Metrics, Test |

### Архитектурные заметки

- **User.role** определён в `auth.types.ts`: `'developer' | 'security' | 'admin'`
- **useAuth()** hook предоставляет доступ к текущему пользователю
- **Sidebar.tsx** уже импортирует `useAuth` — изменения локализованы
- **Badge для Approvals** работает только для security/admin (usePendingRoutesCount с enabled)

### Project Structure Notes

| Файл | Путь | Изменение |
|------|------|-----------|
| Sidebar.tsx | `frontend/admin-ui/src/layouts/` | Добавить ROLE_MENU_ACCESS, фильтрация по роли |
| Sidebar.test.tsx | `frontend/admin-ui/src/layouts/` | Создать или обновить тесты |
| App.tsx | `frontend/admin-ui/src/` | Добавить RequireRole wrapper для protected routes |
| RequireRole.tsx | `frontend/admin-ui/src/features/auth/components/` | Создать компонент (если не существует) |

### References

- [Source: frontend/admin-ui/src/layouts/Sidebar.tsx] — текущая реализация sidebar
- [Source: frontend/admin-ui/src/features/auth/types/auth.types.ts] — типы User, role
- [Source: _bmad-output/planning-artifacts/architecture.md#Frontend Architecture] — React Router v6, features structure
- [Source: _bmad-output/implementation-artifacts/9-2-load-generator-fixes.md] — предыдущая story для контекста

### Тестовые команды

```bash
# Frontend unit тесты
cd frontend/admin-ui
npm run test:run -- Sidebar

# Manual testing
# 1. Залогиниться как developer → проверить что видны только 3 пункта (Dashboard, Routes, Metrics)
# 2. Залогиниться как security → проверить что видны 6 пунктов (без Test, Users, Rate Limits)
# 3. Залогиниться как admin → проверить что видны все 9 пунктов
# 4. Как developer перейти на /test → должен редирект или Access Denied
# 5. Как security перейти на /test → должен редирект или Access Denied
```

### Связанные stories

- Story 2.4 — Role-Based Access Control (backend RBAC)
- Story 2.5 — Admin UI Login Page (AuthContext)
- Story 2.6 — User Management for Admin (Users menu item)
- Story 7.6 — Route History & Upstream Report UI (Integrations menu item)
- Story 8.9 — Страница Test с генератором нагрузки (Test menu item)

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

N/A — реализация прошла без ошибок

### Completion Notes List

- Создан маппинг `ROLE_MENU_ACCESS` для фильтрации пунктов меню по роли
- Рефакторинг `useMemo` в Sidebar.tsx для использования маппинга вместо условной логики
- Добавлена role-based защита для `/rate-limits` и `/test` в App.tsx (только admin)
- Добавлены unit тесты для role-based menu visibility (11 новых тестов в Sidebar.test.tsx)
- Добавлены unit тесты для route protection (6 новых тестов в ProtectedRoute.test.tsx)
- Все 464 frontend теста проходят без регрессий

### File List

- `frontend/admin-ui/src/layouts/Sidebar.tsx` — добавлен ROLE_MENU_ACCESS, рефакторинг menuItems useMemo
- `frontend/admin-ui/src/layouts/Sidebar.test.tsx` — добавлены тесты для AC1, AC2, AC3
- `frontend/admin-ui/src/features/auth/components/ProtectedRoute.test.tsx` — добавлены тесты для AC4
- `frontend/admin-ui/src/App.tsx` — добавлена role-based защита для /rate-limits и /test

### Change Log

- 2026-02-22: Story 9.3 implemented — role-based sidebar visibility and route protection
- 2026-02-22: Code review fixes — added /users route protection tests, improved requiredRole typing, fixed test name to Russian

## Senior Developer Review (AI)

**Reviewer:** Claude Opus 4.5
**Date:** 2026-02-22
**Outcome:** ✅ Approved (after fixes)

### Review Summary

| Category | Status |
|----------|--------|
| AC1 — Developer menu filtering | ✅ Implemented + Tested |
| AC2 — Security menu filtering | ✅ Implemented + Tested |
| AC3 — Admin menu filtering | ✅ Implemented + Tested |
| AC4 — Route protection | ✅ Implemented + Tested (after fix) |
| Code Quality | ✅ Good |
| Test Coverage | ✅ Complete (31 tests) |

### Issues Found & Fixed

| Severity | Issue | Resolution |
|----------|-------|------------|
| HIGH | AC4 — Отсутствовал тест для /users route protection | ✅ Добавлены 3 теста для /users |
| MEDIUM | Слабая типизация `requiredRole?: string` | ✅ Заменено на `User['role']` |
| MEDIUM | Название теста на английском | ✅ Переименовано на русский |
| MEDIUM | Неполный комментарий в ProtectedRoute | ✅ Расширен JSDoc |
| LOW | Комментарий в App.tsx | Оставлено (minor) |
| LOW | Порядок меню в тестах | Оставлено (nice-to-have) |

### Files Modified During Review

- `ProtectedRoute.tsx` — улучшена типизация requiredRole prop
- `ProtectedRoute.test.tsx` — добавлены 3 теста для /users protection
- `Sidebar.test.tsx` — исправлено название теста на русский

