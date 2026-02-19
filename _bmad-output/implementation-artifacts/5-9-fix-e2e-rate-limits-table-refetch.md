# Story 5.9: Fix E2E AC4 — RateLimitsTable Refetch

Status: backlog

## Story

As a **QA Engineer**,
I want the RateLimitsTable to refresh after edit/delete operations,
so that E2E test AC4 (Admin редактирует и удаляет политику) passes.

## Problem Statement

Тест AC4 из Story 5.6 падает потому что:
- После edit/delete API вызовов таблица RateLimitsTable не обновляется
- UI показывает старые данные
- Тест не видит изменения и падает

## Acceptance Criteria

**AC1 — E2E тест AC4 проходит:**

**Given** Admin находится на /rate-limits
**When** Admin редактирует политику через UI
**Then** Таблица показывает обновлённые данные

**When** Admin удаляет политику через UI
**Then** Политика исчезает из таблицы без перезагрузки страницы

## Technical Analysis

**React Query invalidation:**
- После успешной мутации нужно вызвать `queryClient.invalidateQueries(['rate-limits'])`
- Или использовать `refetch()` из useQuery

**Компоненты для проверки:**
- `RateLimitsTable.tsx` — таблица политик
- `RateLimitFormModal.tsx` — форма редактирования
- `useRateLimits.ts` или аналогичный hook — React Query

## Tasks / Subtasks

- [ ] Task 1: Найти где происходят edit/delete мутации
- [ ] Task 2: Добавить invalidateQueries или refetch после успешных мутаций
- [ ] Task 3: Запустить тест AC4, убедиться что проходит
- [ ] Task 4: Проверить что UI работает корректно вручную

## Dev Notes

### Файлы для исследования

- `frontend/admin-ui/src/features/rate-limits/components/RateLimitsTable.tsx`
- `frontend/admin-ui/src/features/rate-limits/components/RateLimitFormModal.tsx`
- `frontend/admin-ui/src/features/rate-limits/api/` — API hooks

### E2E тест AC4 (из epic-5.spec.ts)

```typescript
test('Admin редактирует и удаляет политику', async ({ page }) => {
  // Setup: создать политику через API
  // Login как test-admin
  // Редактирование: изменить requestsPerSecond, сохранить, проверить в таблице
  // Удаление используемой: попытка удаления → ошибка
  // Удаление неиспользуемой: удалить → проверить что исчезла из таблицы
})
```

### Паттерн React Query invalidation

```typescript
const mutation = useMutation({
  mutationFn: updateRateLimit,
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['rate-limits'] })
    message.success('Политика обновлена')
  },
})
```

## References

- [Source: 5-6-e2e-playwright-happy-path.md] — оригинальная история с AC4
- [Source: frontend/admin-ui/e2e/epic-5.spec.ts] — E2E тесты
- [Source: 5-4-rate-limit-policies-management-ui.md] — оригинальная UI история
