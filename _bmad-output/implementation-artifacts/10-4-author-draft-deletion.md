# Story 10.4: Author Can Delete Own Draft Route

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **Developer**,
I want to delete my own draft routes,
so that I can clean up abandoned configurations.

## Feature Context

**Source:** Epic 9 Retrospective (2026-02-22) — FR-04 feedback from Yury (Project Lead)

**Business Value:** Разработчики должны иметь возможность удалять свои draft маршруты, которые больше не нужны. Это позволяет поддерживать чистоту в списке маршрутов и не захламлять его заброшенными конфигурациями.

**Текущее состояние:** Backend полностью реализован (RouteService.delete с проверками ownership). Frontend частично готов — кнопка Delete в таблице работает, но отсутствует на странице деталей маршрута.

## Acceptance Criteria

### AC1: Delete action available for draft route author
**Given** user is the author of a draft route
**When** user views the route (list or details page)
**Then** "Delete" action is available

### AC2: Confirmation modal shown before deletion
**Given** user clicks "Delete" on their draft route
**When** confirmation modal appears
**Then** modal shows warning "Это действие нельзя отменить"
**And** user must confirm to proceed

### AC3: Route deleted successfully
**Given** user confirms deletion
**When** action completes
**Then** route is deleted from database
**And** user is redirected to routes list (if on details page)
**And** success message "Маршрут удалён" is shown

### AC4: Non-authors cannot delete
**Given** user is NOT the author of a draft route
**When** viewing the route
**Then** "Delete" action is NOT available
**And** backend returns 403 if API called directly

### AC5: Non-draft routes cannot be deleted
**Given** a route in pending/published/rejected status
**When** any user attempts deletion
**Then** action is not available in UI
**And** backend returns 409 "Only draft routes can be deleted"

### AC6: Admin can delete any draft route
**Given** user with Admin role
**When** viewing any draft route
**Then** "Delete" action is available
**And** deletion succeeds regardless of author

## Analysis Summary

### Backend Status: ✅ FULLY IMPLEMENTED

Код уже реализован в `RouteService.delete()`:

```kotlin
// backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/RouteService.kt:304-357
fun delete(id: UUID, userId: UUID, username: String, userRole: Role): Mono<Void> {
    return routeRepository.findById(id)
        .switchIfEmpty(Mono.error(NotFoundException("Route not found")))
        .flatMap { route ->
            // Проверяем статус маршрута
            if (route.status != RouteStatus.DRAFT) {
                return@flatMap Mono.error<Void>(
                    ConflictException("Only draft routes can be deleted")
                )
            }
            // Проверяем ownership для Developer
            if (userRole == Role.DEVELOPER && route.createdBy != userId) {
                return@flatMap Mono.error<Void>(
                    AccessDeniedException("You can only modify your own routes")
                )
            }
            routeRepository.delete(route)
                .then(auditService.logDeleted(...))
                .then()
        }
}
```

**Endpoint:** `DELETE /api/v1/routes/{id}`
- 204 No Content — успех
- 403 Forbidden — не автор (Developer)
- 404 Not Found — маршрут не найден
- 409 Conflict — не DRAFT статус

### Frontend Status: ⚠️ PARTIAL IMPLEMENTATION

**RoutesTable (РАБОТАЕТ):**
- Кнопка Delete показывается для draft + owner
- Popconfirm перед удалением
- Loading state на кнопке

**RouteDetailsCard (ТРЕБУЕТ ДОРАБОТКИ):**
- Кнопка Delete **ОТСУТСТВУЕТ** на странице деталей
- Нужно добавить Delete button рядом с Edit/Clone

**canModify() (ТРЕБУЕТ ДОРАБОТКИ):**
- Текущая логика: `status === 'draft' && createdBy === user?.userId`
- Не учитывает роль Admin
- Нужно добавить: `|| user?.role === 'admin'`

## Tasks / Subtasks

- [x] Task 1: Frontend — Add Delete button to RouteDetailsCard (AC: #1, #2, #3)
  - [x] 1.1 Import DeleteOutlined icon from @ant-design/icons
  - [x] 1.2 Import useDeleteRoute hook
  - [x] 1.3 Add `canDelete` check: `(status === 'draft' && createdBy === userId) || role === 'admin'`
  - [x] 1.4 Add Delete button with Popconfirm in extra section
  - [x] 1.5 Navigate to /routes after successful deletion

- [x] Task 2: Frontend — Update canModify logic in RoutesTable (AC: #6)
  - [x] 2.1 Update `canModify()` to include Admin role check
  - [x] 2.2 Ensure Delete button visible for Admin on any draft route

- [x] Task 3: Frontend — Add tests (AC: #1, #4, #5, #6)
  - [x] 3.1 Test: Delete button visible for draft route author
  - [x] 3.2 Test: Delete button NOT visible for non-author
  - [x] 3.3 Test: Delete button NOT visible for non-draft routes
  - [x] 3.4 Test: Delete button visible for Admin on any draft route
  - [x] 3.5 Test: Confirmation modal shown on click
  - [x] 3.6 Test: Navigation to /routes after deletion

- [x] Task 4: Backend — Verify existing tests cover all AC
  - [x] 4.1 Review RouteServiceTest for ownership check tests
  - [x] 4.2 Review RouteControllerIntegrationTest for 403/409 cases
  - [x] 4.3 Add missing tests if any — all tests already exist

## API Dependencies Checklist

**Backend API endpoints, используемые в этой story:**

| Endpoint | Method | Параметры | Статус |
|----------|--------|-----------|--------|
| `/api/v1/routes/{id}` | DELETE | - | ✅ Существует (полностью реализован) |

**Проверки перед началом разработки:**

- [x] DELETE endpoint существует в RouteController
- [x] RouteService.delete() содержит все проверки (status, ownership)
- [x] Audit log записывается при удалении
- [x] 403 возвращается для не-автора (Developer)
- [x] 409 возвращается для не-draft статуса
- [x] useDeleteRoute hook существует в useRoutes.ts
- [x] deleteRoute() существует в routesApi.ts

## Dev Notes

### Архитектура решения

**Минимальные изменения — backend готов, нужен только frontend:**

### Frontend Implementation

**Файлы для изменения:**
| Файл | Назначение |
|------|-----------|
| `frontend/admin-ui/src/features/routes/components/RouteDetailsCard.tsx` | Добавить кнопку Delete |
| `frontend/admin-ui/src/features/routes/components/RoutesTable.tsx` | Обновить canModify для Admin |
| `frontend/admin-ui/src/features/routes/components/RouteDetailsCard.test.tsx` | Добавить тесты |
| `frontend/admin-ui/src/features/routes/components/RoutesTable.test.tsx` | Добавить тест для Admin |

**RouteDetailsCard.tsx — добавить кнопку Delete:**

```tsx
import { DeleteOutlined } from '@ant-design/icons'
import { useDeleteRoute } from '../hooks/useRoutes'
import { Popconfirm } from 'antd'

// Внутри компонента:
const deleteMutation = useDeleteRoute()

// Проверка прав на удаление (такая же логика как canSubmit, но с Admin)
const canDelete = route.status === 'draft' &&
  (route.createdBy === user?.userId || user?.role === 'admin')

// Обработчик удаления с редиректом
const handleDelete = async () => {
  try {
    await deleteMutation.mutateAsync(route.id)
    navigate('/routes')
  } catch {
    // Ошибка уже обработана в useDeleteRoute hook
  }
}

// В extra section рядом с другими кнопками:
{canDelete && (
  <Popconfirm
    title="Удалить маршрут?"
    description="Это действие нельзя отменить"
    onConfirm={handleDelete}
    okText="Да"
    okType="danger"
    cancelText="Нет"
  >
    <Button
      type="default"
      danger
      icon={<DeleteOutlined />}
      loading={deleteMutation.isPending}
    >
      Удалить
    </Button>
  </Popconfirm>
)}
```

**RoutesTable.tsx — обновить canModify:**

```tsx
// Было (строки 226-229):
const canModify = useCallback((route: Route): boolean => {
  if (route.status !== 'draft') return false
  return route.createdBy === user?.userId
}, [user?.userId])

// Стало:
const canModify = useCallback((route: Route): boolean => {
  if (route.status !== 'draft') return false
  // Developer может редактировать/удалять только свои маршруты
  // Admin может редактировать/удалять любые draft маршруты
  return route.createdBy === user?.userId || user?.role === 'admin'
}, [user?.userId, user?.role])
```

### Тестирование

**RouteDetailsCard.test.tsx — добавить тесты:**

```typescript
describe('Delete button', () => {
  it('показывает кнопку Delete для автора draft маршрута', () => {
    render(<RouteDetailsCard route={draftRoute} />, {
      wrapper: withUser({ userId: draftRoute.createdBy, role: 'developer' })
    })
    expect(screen.getByText('Удалить')).toBeInTheDocument()
  })

  it('скрывает кнопку Delete для не-автора', () => {
    render(<RouteDetailsCard route={draftRoute} />, {
      wrapper: withUser({ userId: 'other-user', role: 'developer' })
    })
    expect(screen.queryByText('Удалить')).not.toBeInTheDocument()
  })

  it('скрывает кнопку Delete для non-draft маршрутов', () => {
    render(<RouteDetailsCard route={publishedRoute} />, {
      wrapper: withUser({ userId: publishedRoute.createdBy, role: 'developer' })
    })
    expect(screen.queryByText('Удалить')).not.toBeInTheDocument()
  })

  it('показывает кнопку Delete для Admin на чужом draft маршруте', () => {
    render(<RouteDetailsCard route={draftRoute} />, {
      wrapper: withUser({ userId: 'admin-user', role: 'admin' })
    })
    expect(screen.getByText('Удалить')).toBeInTheDocument()
  })

  it('показывает confirmation при клике на Delete', async () => {
    render(<RouteDetailsCard route={draftRoute} />, {
      wrapper: withUser({ userId: draftRoute.createdBy, role: 'developer' })
    })
    await userEvent.click(screen.getByText('Удалить'))
    expect(screen.getByText('Это действие нельзя отменить')).toBeInTheDocument()
  })
})
```

### Project Structure Notes

- Изменения только во frontend (backend уже готов)
- Следуем паттерну Rollback button из Story 10.3
- Используем существующий useDeleteRoute hook без изменений
- Popconfirm компонент уже используется в RoutesTable — переиспользуем паттерн

### References

- [Source: architecture.md#Frontend Architecture] — React Query + Ant Design
- [Source: backend/gateway-admin/src/main/kotlin/com/company/gateway/admin/service/RouteService.kt:304-357] — существующий delete метод
- [Source: frontend/admin-ui/src/features/routes/components/RoutesTable.tsx:218-333] — существующая реализация Delete в таблице
- [Source: frontend/admin-ui/src/features/routes/components/RouteDetailsCard.tsx] — компонент для добавления кнопки
- [Source: epics.md#Story 10.4] — acceptance criteria
- [Source: 10-3-security-route-rollback.md] — паттерн добавления кнопки с confirmation

## Previous Story Learnings (10.3)

**Из Story 10.3 (Security Role Route Rollback):**

1. **Role check в lowercase** — тип User.role в lowercase ('developer', 'admin'), НЕ в uppercase
2. **Button placement** — кнопки в extra section Card компонента
3. **Confirmation pattern** — Popconfirm для деструктивных действий
4. **mutateAsync + try/catch** — для навигации после успешной мутации
5. **Loading state** — `loading={mutation.isPending}` на кнопке

**Применимо к текущей story:**
- Использовать lowercase роли: `user?.role === 'admin'`
- Popconfirm с okType="danger" для delete
- Navigate после успешного удаления
- Loading state на кнопке Delete

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

### Completion Notes List

- Task 1: Добавлена кнопка Delete в RouteDetailsCard с Popconfirm, canDelete check учитывает owner и admin
- Task 2: Обновлена логика canModify в RoutesTable для учёта роли Admin
- Task 3: Добавлено 9 тестов для Delete UI в RouteDetailsCard.test.tsx, 2 теста для Admin в RoutesPage.test.tsx
- Task 4: Проверены backend тесты — все AC уже покрыты (AC4, AC5, AC6 в RouteControllerIntegrationTest, RbacIntegrationTest, OwnershipServiceTest)
- Исправлен RouteDetailsPage.test.tsx — добавлены моки для useRollbackRoute и useDeleteRoute
- Все 515 frontend тестов проходят

**Code Review Fixes (2026-02-22):**
- M1: Добавлен тест для rejected status (AC #5 coverage)
- M2: Добавлен тест для published status (AC #5 coverage)
- M3: Добавлен sprint-status.yaml в File List
- L2: Обновлён header комментарий (Story 10.4 добавлен)
- После исправлений: 517 тестов проходят

### File List

- frontend/admin-ui/src/features/routes/components/RouteDetailsCard.tsx (modified)
- frontend/admin-ui/src/features/routes/components/RouteDetailsCard.test.tsx (modified)
- frontend/admin-ui/src/features/routes/components/RoutesTable.tsx (modified)
- frontend/admin-ui/src/features/routes/components/RoutesPage.test.tsx (modified)
- frontend/admin-ui/src/features/routes/components/RouteDetailsPage.test.tsx (modified)
- _bmad-output/implementation-artifacts/sprint-status.yaml (modified)

### Change Log

- 2026-02-22: Story 10.4 implemented — Delete button on RouteDetailsCard, Admin role support in RoutesTable, comprehensive test coverage
- 2026-02-22: Code review fixes — 3 MEDIUM issues fixed (rejected/published tests, File List), 1 LOW issue fixed (header comment)
