# Story 11.6: Role Check Refactoring

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **developer**,
I want centralized role-checking helper functions,
so that role permission checks are consistent and type-safe across the codebase.

## Feature Context

**Source:** Epic 10 Retrospective (2026-02-23) — PI-01 (Process Improvement)
**Business Value:** Повторяющийся баг с UPPERCASE vs lowercase ролей приводил к ошибкам доступа. Централизованные helpers предотвратят такие ошибки и упростят изменение логики доступа в будущем.

## Acceptance Criteria

### AC1: Централизованные helper-функции созданы
**Given** developer needs to check user permissions
**When** implementing a new feature
**Then** they use centralized helpers: `canRollback()`, `canDelete()`, `canModify()`, etc.

### AC2: TypeScript literal types предотвращают ошибки
**Given** TypeScript literal type for roles exists
**When** developer writes incorrect role string (e.g., 'SECURITY' instead of 'security')
**Then** TypeScript compiler shows error

### AC3: Единая точка изменений
**Given** permission logic changes
**When** developer updates the helper function
**Then** all usages across the codebase are automatically updated

## Tasks / Subtasks

- [x] Task 1: Create shared role utilities (AC: #1, #2)
  - [x] 1.1 Create `frontend/admin-ui/src/shared/constants/roles.ts` with role constants
  - [x] 1.2 Create `frontend/admin-ui/src/shared/utils/rolePermissions.ts` with helper functions
  - [x] 1.3 Export utilities from `shared/index.ts`

- [x] Task 2: Implement permission helper functions (AC: #1, #3)
  - [x] 2.1 `canRollback(route, user)` — Security/Admin для published маршрутов
  - [x] 2.2 `canDelete(route, user)` — автор или Admin для draft маршрутов
  - [x] 2.3 `canModify(route, user)` — автор или Admin для draft маршрутов
  - [x] 2.4 `canApprove(user)` — Security/Admin могут одобрять маршруты
  - [x] 2.5 `isAdminOrSecurity(user)` — общая проверка для административных действий
  - [x] 2.6 `isAdmin(user)` — проверка admin роли

- [x] Task 3: Refactor existing code to use helpers (AC: #3)
  - [x] 3.1 Update `RouteDetailsCard.tsx` — заменить inline проверки
  - [x] 3.2 Update `RoutesTable.tsx` — заменить canModify inline логику
  - [x] 3.3 Update `useApprovals.ts` — заменить role проверку
  - [x] 3.4 Update `RateLimitsPage.tsx` — использовать isAdmin()

- [x] Task 4: Consolidate role constants (AC: #2)
  - [x] 4.1 Move role options to shared constants (UsersTable, UserFormModal)
  - [x] 4.2 Update imports in all affected files
  - [x] 4.3 Remove duplicate role definitions

- [x] Task 5: Unit tests for permission helpers (AC: #1, #2, #3)
  - [x] 5.1 Test canRollback() — all role/status combinations
  - [x] 5.2 Test canDelete() — all role/author combinations
  - [x] 5.3 Test canModify() — all role/author combinations
  - [x] 5.4 Test isAdmin/isAdminOrSecurity

### Review Follow-ups (AI)

- [x] [AI-Review][HIGH] Use canApprove() instead of isAdminOrSecurity() in useApprovals.ts for semantic clarity
- [x] [AI-Review][LOW] Add isDeveloper() helper for consistency with isAdmin()
- [x] [AI-Review][LOW] Refactor MetricsPage, AuditPage, IntegrationsPage to use isDeveloper() helper
- [ ] [AI-Review][MEDIUM] Pre-existing TypeScript errors in test files (RouteDetailsCard.test.tsx:416,430,460,476,498,579) — out of scope, needs separate story

## API Dependencies Checklist

<!-- Секция не применима — story чисто frontend рефакторинг. -->

Эта story — чистый frontend рефакторинг без изменений backend API.

## Dev Notes

### Текущее состояние (ПРОБЛЕМЫ)

**1. Дублирование типов ролей:**

```typescript
// auth.types.ts:6
role: 'developer' | 'security' | 'admin'

// user.types.ts:10
export type UserRole = 'developer' | 'security' | 'admin'
```

Два разных типа для одного и того же. Использовать `UserRole` везде.

**2. Дублирование проверок ролей:**

```typescript
// RouteDetailsCard.tsx:50-51
const canRollback = route.status === 'published' &&
  (user?.role === 'security' || user?.role === 'admin')

// useApprovals.ts:52
enabled: user?.role === 'security' || user?.role === 'admin',
```

Одинаковая логика дублируется. Создать `isAdminOrSecurity()`.

**3. Дублирование констант:**

```typescript
// UsersTable.tsx:22-35
const roleColors: Record<UserRole, string> = { developer: 'blue', ... }
const roleLabels: Record<UserRole, string> = { developer: 'Developer', ... }

// UserFormModal.tsx:16-20
const roleOptions = [
  { value: 'developer', label: 'Developer' },
  ...
]
```

Повторяющиеся константы. Создать единый `ROLE_OPTIONS` в shared.

### Целевая структура

```
frontend/admin-ui/src/shared/
├── constants/
│   ├── roles.ts       # НОВЫЙ: UserRole, ROLE_OPTIONS, ROLE_COLORS, ROLE_LABELS
│   └── index.ts
├── utils/
│   ├── rolePermissions.ts  # НОВЫЙ: canRollback, canDelete, isAdmin, etc.
│   └── index.ts
└── index.ts           # Экспорт всего
```

### API Helper Functions

```typescript
// shared/utils/rolePermissions.ts

import type { UserRole } from '../constants/roles'

interface MinimalUser {
  userId?: string
  role?: UserRole
}

interface MinimalRoute {
  status: string
  createdBy?: string
}

/**
 * Проверяет, может ли пользователь откатить маршрут.
 * Rollback доступен Security/Admin для published маршрутов.
 */
export const canRollback = (route: MinimalRoute, user?: MinimalUser): boolean => {
  return route.status === 'published' && isAdminOrSecurity(user)
}

/**
 * Проверяет, может ли пользователь удалить маршрут.
 * Delete доступен автору или Admin для draft маршрутов.
 */
export const canDelete = (route: MinimalRoute, user?: MinimalUser): boolean => {
  if (route.status !== 'draft') return false
  return route.createdBy === user?.userId || isAdmin(user)
}

/**
 * Проверяет, может ли пользователь редактировать маршрут.
 * Редактирование доступно автору или Admin для draft маршрутов.
 */
export const canModify = (route: MinimalRoute, user?: MinimalUser): boolean => {
  if (route.status !== 'draft') return false
  return route.createdBy === user?.userId || isAdmin(user)
}

/**
 * Проверяет роль Admin или Security.
 */
export const isAdminOrSecurity = (user?: MinimalUser): boolean => {
  return user?.role === 'security' || user?.role === 'admin'
}

/**
 * Проверяет роль Admin.
 */
export const isAdmin = (user?: MinimalUser): boolean => {
  return user?.role === 'admin'
}
```

### Константы ролей

```typescript
// shared/constants/roles.ts

/**
 * Роль пользователя в системе.
 * Lowercase — стандарт проекта (см. CLAUDE.md).
 */
export type UserRole = 'developer' | 'security' | 'admin'

/**
 * Все доступные роли (для итерации).
 */
export const ROLES: readonly UserRole[] = ['developer', 'security', 'admin'] as const

/**
 * Опции ролей для форм и фильтров.
 */
export const ROLE_OPTIONS: { value: UserRole; label: string }[] = [
  { value: 'developer', label: 'Developer' },
  { value: 'security', label: 'Security' },
  { value: 'admin', label: 'Admin' },
]

/**
 * Цвета для отображения ролей в UI.
 */
export const ROLE_COLORS: Record<UserRole, string> = {
  developer: 'blue',
  security: 'orange',
  admin: 'purple',
}

/**
 * Метки ролей для отображения.
 */
export const ROLE_LABELS: Record<UserRole, string> = {
  developer: 'Developer',
  security: 'Security',
  admin: 'Admin',
}
```

### Файлы для рефакторинга

| Файл | Текущий код | Замена |
|------|-------------|--------|
| `RouteDetailsCard.tsx:50-51` | `(user?.role === 'security' \|\| user?.role === 'admin')` | `isAdminOrSecurity(user)` |
| `RouteDetailsCard.tsx:54-55` | inline canDelete | `canDelete(route, user)` |
| `RoutesTable.tsx:227-232` | inline canModify | `canModify(route, user)` |
| `useApprovals.ts:52` | `user?.role === 'security' \|\| user?.role === 'admin'` | `isAdminOrSecurity(user)` |
| `RateLimitsPage.tsx:26` | `user?.role === 'admin'` | `isAdmin(user)` |
| `UsersTable.tsx:22-35` | local roleColors, roleLabels | import from shared |
| `UserFormModal.tsx:16-20` | local roleOptions | import ROLE_OPTIONS from shared |

### Типизация

**Проблема:** `auth.types.ts` использует inline literal type, а `user.types.ts` экспортирует `UserRole`.

**Решение:**
1. Удалить inline literal type из `auth.types.ts`
2. Импортировать `UserRole` из shared constants
3. Удалить `UserRole` из `user.types.ts` (будет в shared)

### Тестирование

Unit тесты для всех permission helpers с полным покрытием комбинаций:

```typescript
// rolePermissions.test.ts

describe('canRollback', () => {
  it('возвращает true для Security на published маршруте', () => {
    const route = { status: 'published' }
    const user = { role: 'security' as const }
    expect(canRollback(route, user)).toBe(true)
  })

  it('возвращает false для Developer на published маршруте', () => {
    const route = { status: 'published' }
    const user = { role: 'developer' as const }
    expect(canRollback(route, user)).toBe(false)
  })

  it('возвращает false для Admin на draft маршруте', () => {
    const route = { status: 'draft' }
    const user = { role: 'admin' as const }
    expect(canRollback(route, user)).toBe(false)
  })
})
```

### Project Structure Notes

- Utilities создаются в `shared/utils/` согласно architecture.md
- Constants создаются в `shared/constants/` — новая директория (расширение структуры)
- Тесты создаются в `__tests__/` рядом с файлами
- Комментарии на русском языке (CLAUDE.md)

### References

- [Source: RouteDetailsCard.tsx:50-55] — текущая inline логика canRollback, canDelete
- [Source: RoutesTable.tsx:227-232] — текущая inline логика canModify
- [Source: useApprovals.ts:52] — дублирующаяся проверка роли
- [Source: auth.types.ts:6] — inline literal type для role
- [Source: user.types.ts:10] — экспортированный UserRole type
- [Source: UsersTable.tsx:22-35] — дублирующиеся константы roleColors, roleLabels
- [Source: UserFormModal.tsx:16-20] — дублирующийся roleOptions
- [Source: Sidebar.tsx:30-44] — ROLE_MENU_ACCESS (хороший пример централизации)
- [Source: CLAUDE.md] — правила проекта, naming conventions

### Scope Notes

**In Scope:**
- Создание shared/constants/roles.ts
- Создание shared/utils/rolePermissions.ts
- Рефакторинг существующих проверок ролей
- Unit тесты для helpers
- Удаление дублирующихся констант

**Out of Scope:**
- Backend изменения
- Изменение Sidebar ROLE_MENU_ACCESS (уже хорошо организовано)
- Изменение ProtectedRoute (работает корректно)
- Изменение App.tsx requiredRole (работает корректно)

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

### Completion Notes List

- ✅ Task 1: Созданы shared role utilities
  - `shared/constants/roles.ts` — UserRole type, ROLES, ROLE_OPTIONS, ROLE_COLORS, ROLE_LABELS
  - `shared/utils/rolePermissions.ts` — isAdmin, isAdminOrSecurity, canApprove, canRollback, canDelete, canModify
  - Экспорт через `shared/index.ts`

- ✅ Task 2: Реализованы все permission helper functions
  - Все 7 функций реализованы с полной документацией на русском языке (isAdmin, isDeveloper, isAdminOrSecurity, canApprove, canRollback, canDelete, canModify)
  - MinimalUser и MinimalRoute interfaces для типобезопасности

- ✅ Task 3: Рефакторинг существующего кода
  - RouteDetailsCard.tsx — canRollback и canDelete используют helpers
  - RoutesTable.tsx — canModify использует helper
  - useApprovals.ts — enabled условие использует canApprove (review fix)
  - RateLimitsPage.tsx — isAdmin использует helper
  - MetricsPage.tsx — isDeveloper использует helper (review fix)
  - AuditPage.tsx — isDeveloper использует helper (review fix)
  - IntegrationsPage.tsx — isDeveloper использует helper (review fix)

- ✅ Task 4: Консолидация констант ролей
  - UsersTable.tsx — импортирует ROLE_COLORS, ROLE_LABELS из shared
  - UserFormModal.tsx — импортирует ROLE_OPTIONS из shared
  - auth.types.ts — импортирует UserRole из shared
  - user.types.ts — re-export UserRole для обратной совместимости

- ✅ Task 5: Unit тесты для permission helpers
  - 42 теста покрывают все комбинации ролей и статусов (review fix: +5 для isDeveloper)
  - Тесты для isAdmin, isDeveloper, isAdminOrSecurity, canApprove, canRollback, canDelete, canModify

### File List

**New files:**
- frontend/admin-ui/src/shared/constants/roles.ts
- frontend/admin-ui/src/shared/utils/rolePermissions.ts
- frontend/admin-ui/src/shared/utils/rolePermissions.test.ts
- frontend/admin-ui/src/shared/utils/index.ts

**Modified files:**
- frontend/admin-ui/src/shared/constants/index.ts
- frontend/admin-ui/src/shared/index.ts
- frontend/admin-ui/src/features/routes/components/RouteDetailsCard.tsx
- frontend/admin-ui/src/features/routes/components/RoutesTable.tsx
- frontend/admin-ui/src/features/approval/hooks/useApprovals.ts
- frontend/admin-ui/src/features/rate-limits/components/RateLimitsPage.tsx
- frontend/admin-ui/src/features/users/components/UsersTable.tsx
- frontend/admin-ui/src/features/users/components/UserFormModal.tsx
- frontend/admin-ui/src/features/auth/types/auth.types.ts
- frontend/admin-ui/src/features/users/types/user.types.ts
- frontend/admin-ui/src/features/metrics/components/MetricsPage.tsx (review fix)
- frontend/admin-ui/src/features/audit/components/AuditPage.tsx (review fix)
- frontend/admin-ui/src/features/audit/components/IntegrationsPage.tsx (review fix)

## Change Log

- 2026-02-23: Story 11.6 implemented — centralized role checking helpers created, 37 unit tests pass, existing code refactored to use helpers
- 2026-02-23: Code review fixes — added isDeveloper() helper, refactored MetricsPage/AuditPage/IntegrationsPage, changed useApprovals to use canApprove(), 42 unit tests pass
