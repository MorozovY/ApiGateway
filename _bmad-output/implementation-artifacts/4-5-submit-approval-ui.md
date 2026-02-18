# Story 4.5: Submit for Approval UI

Status: done

## Story

As a **Developer**,
I want to submit my draft routes for approval from the UI,
So that I can initiate the review process easily.

## Acceptance Criteria

**AC1 — Кнопка "Submit for Approval" для draft маршрута:**

**Given** пользователь просматривает draft маршрут, который ему принадлежит
**When** страница загружается
**Then** кнопка "Отправить на согласование" отображается prominently (в actions карточки)

**AC2 — Модальное окно подтверждения:**

**Given** пользователь нажимает "Отправить на согласование"
**When** действие срабатывает
**Then** модальное окно подтверждения открывается с:
- Заголовок: "Отправить на согласование"
- Текст: "Маршрут будет отправлен в Security на проверку. Вы не сможете редактировать его до одобрения или отклонения."
- Кнопки: "Отмена", "Отправить"

**AC3 — Успешная отправка на согласование:**

**Given** пользователь подтверждает отправку
**When** API вызов `POST /api/v1/routes/{id}/submit` успешно завершается
**Then** модальное окно закрывается
**And** toast уведомление: "Маршрут отправлен на согласование"
**And** страница обновляется с новым статусом (badge "На согласовании")
**And** кнопка "Отправить на согласование" заменяется индикатором статуса

**AC4 — Статус "pending" — маршрут ожидает одобрения:**

**Given** пользователь просматривает свой маршрут в статусе `pending`
**When** страница загружается
**Then** отображается сообщение: "Ожидает одобрения Security"
**And** никаких action-кнопок редактирования нет

**AC5 — Статус "rejected" — маршрут отклонён:**

**Given** пользователь просматривает свой маршрут в статусе `rejected`
**When** страница загружается
**Then** причина отклонения отображается prominently (блок с текстом причины и именем отклонившего)
**And** кнопка "Редактировать и повторно отправить" доступна

**AC6 — "Редактировать и повторно отправить" для rejected маршрута:**

**Given** пользователь нажимает "Редактировать и повторно отправить" на rejected маршруте
**When** действие срабатывает
**Then** пользователь переходит на страницу редактирования маршрута (`/routes/{id}/edit`)

## Tasks / Subtasks

- [x] Task 1: Расширить Route interface новыми полями (AC3, AC4, AC5)
  - [x] Добавить в `route.types.ts`: `rejectionReason`, `rejectorUsername`, `approverUsername`, `approvedAt`, `rejectedAt`

- [x] Task 2: Добавить функцию `submitForApproval` в routesApi.ts (AC3)
  - [x] `POST /api/v1/routes/{id}/submit`

- [x] Task 3: Создать hook `useSubmitRoute` в useRoutes.ts (AC3)
  - [x] useMutation, инвалидировать кэш маршрута и списка после успеха
  - [x] toast "Маршрут отправлен на согласование" при успехе

- [x] Task 4: Обновить RouteDetailsCard — добавить UI для Submit flow (AC1, AC2, AC3, AC4, AC5, AC6)
  - [x] Кнопка "Отправить на согласование" для draft + owner
  - [x] Modal подтверждения с нужным текстом
  - [x] Для pending: Alert/Badge "Ожидает одобрения Security" (без Edit-кнопок)
  - [x] Для rejected: блок с причиной отклонения + кнопка "Редактировать и повторно отправить"

- [x] Task 5: Тесты компонентов
  - [x] Тест: отображает кнопку "Отправить на согласование" для draft + owner
  - [x] Тест: не отображает кнопку для non-draft маршрута
  - [x] Тест: открывает модальное окно при клике
  - [x] Тест: закрывает модальное и показывает toast при успешном submit
  - [x] Тест: для pending маршрута — показывает "Ожидает одобрения Security"
  - [x] Тест: для pending маршрута — нет кнопки Edit
  - [x] Тест: для rejected маршрута — показывает причину отклонения
  - [x] Тест: для rejected маршрута — показывает кнопку "Редактировать и повторно отправить"
  - [x] Тест: кнопка "Редактировать и повторно отправить" навигирует на страницу редактирования

## Dev Notes

### API Backend — что уже готово

Backend полностью реализован в Stories 4.1–4.4:

- `POST /api/v1/routes/{id}/submit` — эндпоинт готов (Story 4.1)
  - Принимает `DRAFT` → `PENDING` (Story 4.1)
  - Принимает `REJECTED` → `PENDING`, очищает rejection-поля (Story 4.4)
  - Возвращает 409 если статус не `draft` и не `rejected`
  - Возвращает 403 если маршрут принадлежит другому пользователю

- `GET /api/v1/routes/{id}` — ответ уже включает (Story 4.4):
  - `rejectionReason: String?`
  - `rejectorUsername: String?`
  - `rejectedAt: Instant?`
  - `approverUsername: String?`
  - `approvedAt: Instant?`

Никаких изменений на бэкенде НЕ требуется.

### Task 1 — Расширение Route interface

Файл: `frontend/admin-ui/src/features/routes/types/route.types.ts`

Текущий interface `Route` НЕ содержит полей об approval/rejection. Добавить:

```typescript
export interface Route {
  // ...существующие поля...
  rejectionReason?: string | null
  rejectorUsername?: string | null
  rejectedAt?: string | null
  approverUsername?: string | null
  approvedAt?: string | null
}
```

> Эти поля nullable/optional, т.к. большинство маршрутов их не имеют.

### Task 2 — submitForApproval в routesApi.ts

Файл: `frontend/admin-ui/src/features/routes/api/routesApi.ts`

Добавить функцию:
```typescript
/**
 * Отправка маршрута на согласование.
 *
 * POST /api/v1/routes/{id}/submit
 *
 * Работает для статусов draft и rejected.
 */
export async function submitForApproval(id: string): Promise<Route> {
  const { data } = await axios.post<Route>(`${BASE_URL}/${id}/submit`)
  return data
}
```

### Task 3 — hook useSubmitRoute

Файл: `frontend/admin-ui/src/features/routes/hooks/useRoutes.ts`

Паттерн по аналогии с `useCloneRoute`:
```typescript
/**
 * Hook для отправки маршрута на согласование.
 *
 * После успеха инвалидирует кэш маршрута и списка.
 */
export function useSubmitRoute() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => routesApi.submitForApproval(id),
    onSuccess: (data) => {
      // Инвалидируем и конкретный маршрут, и список
      queryClient.invalidateQueries({ queryKey: [ROUTES_QUERY_KEY], refetchType: 'all' })
      message.success('Маршрут отправлен на согласование')
    },
    onError: (error: Error) => {
      message.error(error.message || 'Ошибка при отправке на согласование')
    },
  })
}
```

### Task 4 — Обновление RouteDetailsCard

Файл: `frontend/admin-ui/src/features/routes/components/RouteDetailsCard.tsx`

**Ключевые изменения в компоненте:**

1. Импортировать `useSubmitRoute` и `Modal`, `Alert` из antd
2. Добавить state `submitModalVisible`
3. Логика видимости действий по статусу:
   - `draft` + owner: показать кнопку "Отправить на согласование" + существующий Edit
   - `pending` + owner: показать Alert "Ожидает одобрения Security", скрыть Edit
   - `rejected` + owner: показать блок rejection + кнопку "Редактировать и повторно отправить"
4. Модальное окно подтверждения submit

```tsx
// Кнопка "Отправить на согласование" — только для draft + owner
const canSubmit = route.status === 'draft' && route.createdBy === user?.userId

// Кнопка "Редактировать и повторно отправить" — только для rejected + owner
const canResubmit = route.status === 'rejected' && route.createdBy === user?.userId

// Ожидает одобрения — только для pending + owner
const isPendingOwner = route.status === 'pending' && route.createdBy === user?.userId
```

**Блок rejection reason** (для статуса `rejected`):
```tsx
{canResubmit && route.rejectionReason && (
  <Alert
    type="error"
    message="Маршрут отклонён"
    description={
      <>
        <div><strong>Причина:</strong> {route.rejectionReason}</div>
        {route.rejectorUsername && (
          <div><strong>Отклонил:</strong> {route.rejectorUsername}</div>
        )}
      </>
    }
    showIcon
    style={{ marginBottom: 16 }}
  />
)}
```

**Блок pending** (для статуса `pending`, owner):
```tsx
{isPendingOwner && (
  <Alert
    type="info"
    message="Ожидает одобрения Security"
    showIcon
    style={{ marginBottom: 16 }}
  />
)}
```

**Кнопки в extra секции карточки:**
```tsx
{canSubmit && (
  <Button
    type="primary"
    icon={<SendOutlined />}
    onClick={() => setSubmitModalVisible(true)}
  >
    Отправить на согласование
  </Button>
)}

{canResubmit && (
  <Button
    type="primary"
    icon={<EditOutlined />}
    onClick={handleEdit}
  >
    Редактировать и повторно отправить
  </Button>
)}
```

**Модальное окно:**
```tsx
<Modal
  title="Отправить на согласование"
  open={submitModalVisible}
  onCancel={() => setSubmitModalVisible(false)}
  onOk={handleSubmitConfirm}
  okText="Отправить"
  cancelText="Отмена"
  confirmLoading={submitMutation.isPending}
>
  <p>
    Маршрут будет отправлен в Security на проверку. Вы не сможете
    редактировать его до одобрения или отклонения.
  </p>
</Modal>
```

**handleSubmitConfirm:**
```tsx
const handleSubmitConfirm = async () => {
  try {
    await submitMutation.mutateAsync(route.id)
    setSubmitModalVisible(false)
  } catch {
    // Ошибка уже обработана в useSubmitRoute (message.error)
  }
}
```

> Кнопка обычного Edit (из Stories 3.5/3.6) — оставить только для статуса `draft`. Для `rejected` — заменить на "Редактировать и повторно отправить".

### Структура файлов для изменения

| Файл | Действие |
|------|---------|
| `frontend/admin-ui/src/features/routes/types/route.types.ts` | Добавить поля rejection/approval в `Route` interface |
| `frontend/admin-ui/src/features/routes/api/routesApi.ts` | Добавить `submitForApproval(id)` |
| `frontend/admin-ui/src/features/routes/hooks/useRoutes.ts` | Добавить `useSubmitRoute` hook |
| `frontend/admin-ui/src/features/routes/components/RouteDetailsCard.tsx` | Основной UI компонент submit flow |
| `frontend/admin-ui/src/features/routes/components/RouteDetailsCard.test.tsx` | Тесты (создать новый файл) |

### Архитектурные требования

- **Reactive patterns**: React Query `useMutation` — без прямых state-обновлений, только инвалидация кэша
- **Структура**: все изменения в `features/routes/` — submit flow принадлежит routes, не approval
- **Нет новых routes**: `/approvals` (Story 4.6) не реализуем — только submit
- **Комментарии**: только на русском языке
- **Названия тестов**: только на русском языке
- **UI библиотека**: только Ant Design компоненты (`Modal`, `Alert`, `Button`, `message`)
- **Axios instance**: `@shared/utils/axios` (не нативный fetch)
- **camelCase**: JSON поля (соответствует существующей структуре `Route`)
- **RFC 7807**: ошибки обрабатываются через axios interceptor в `@shared/utils/axios`

### Паттерны из предыдущих историй

**Toast notifications** (из useRoutes.ts, useDeleteRoute):
```typescript
message.success('Маршрут отправлен на согласование')
message.error(error.message || 'Ошибка при отправке на согласование')
```

**Инвалидация кэша** (из useCloneRoute):
```typescript
queryClient.invalidateQueries({ queryKey: [ROUTES_QUERY_KEY], refetchType: 'all' })
```

**Проверка owner** (из RouteDetailsCard):
```typescript
const canEdit = route.status === 'draft' && route.createdBy === user?.userId
```

**Тестирование мутаций** (из RouteDetailsPage.test.tsx):
```typescript
let mockSubmitMutateAsync = vi.fn()
let mockSubmitIsPending = false

vi.mock('../hooks/useRoutes', () => ({
  useRoute: ...,
  useCloneRoute: ...,
  useSubmitRoute: () => ({
    mutateAsync: mockSubmitMutateAsync,
    isPending: mockSubmitIsPending,
  }),
}))
```

**Используемые иконки** (уже доступны в @ant-design/icons):
- `SendOutlined` — для кнопки "Отправить на согласование"
- `EditOutlined` — уже используется в файле

### Тестовый подход

Паттерн из `RouteDetailsPage.test.tsx`:
- Использовать `renderWithMockAuth` из `../../../test/test-utils`
- Мокать `../hooks/useRoutes` полностью
- Мокать `@features/auth` для `useAuth`
- Мокать `react-router-dom` для `useNavigate` и `useParams`
- Имена тестов на русском языке

**Тест файл**: `RouteDetailsCard.test.tsx` в `features/routes/components/`

Ключевые тест-кейсы:
```
describe('Submit for Approval UI', () => {
  it('отображает кнопку "Отправить на согласование" для draft маршрута владельца')
  it('скрывает кнопку submit для non-draft маршрута')
  it('открывает модальное окно при клике на submit')
  it('вызывает API при подтверждении и закрывает modal')
  it('показывает "Ожидает одобрения Security" для pending маршрута владельца')
  it('не показывает Edit для pending маршрута')
  it('показывает причину отклонения для rejected маршрута')
  it('показывает кнопку "Редактировать и повторно отправить" для rejected маршрута владельца')
  it('навигирует на /edit при клике "Редактировать и повторно отправить"')
})
```

### Project Structure Notes

```
frontend/admin-ui/src/features/routes/
├── types/
│   └── route.types.ts              # ИЗМЕНИТЬ — добавить rejection/approval поля в Route
├── api/
│   └── routesApi.ts                # ИЗМЕНИТЬ — добавить submitForApproval()
├── hooks/
│   └── useRoutes.ts                # ИЗМЕНИТЬ — добавить useSubmitRoute hook
└── components/
    ├── RouteDetailsCard.tsx         # ИЗМЕНИТЬ — основной UI submit flow
    └── RouteDetailsCard.test.tsx    # СОЗДАТЬ — тесты нового функционала
```

> `features/approval/` остаётся пустым (содержит `.gitkeep`). Story 4.5 полностью реализуется в `features/routes/` — submit принадлежит flow владения маршрутом. Story 4.6 (pending approvals list) будет в `features/approval/`.

### References

- [Source: planning-artifacts/epics.md#Story-4.5] — Story requirements и AC
- [Source: planning-artifacts/architecture.md#Frontend-Architecture] — features/ структура, React Query, Ant Design
- [Source: implementation-artifacts/4-4-route-status-tracking.md] — RouteDetailResponse с rejection/approval полями
- [Source: frontend/admin-ui/src/features/routes/types/route.types.ts] — текущий Route interface
- [Source: frontend/admin-ui/src/features/routes/api/routesApi.ts] — паттерн axios вызовов
- [Source: frontend/admin-ui/src/features/routes/hooks/useRoutes.ts] — паттерн useMutation
- [Source: frontend/admin-ui/src/features/routes/components/RouteDetailsCard.tsx] — компонент для расширения
- [Source: frontend/admin-ui/src/features/routes/components/RouteDetailsPage.test.tsx] — паттерн тестирования

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-5-20250929

### Debug Log References

_Без значимых debug-событий. Один исправленный момент: тест "открывает модальное окно" использовал `getByText('Отправить на согласование')` — после открытия Modal текст присутствовал в двух местах (кнопка + заголовок Modal). Исправлено на поиск по уникальному тексту предупреждения._

_Регрессия в RouteDetailsPage.test.tsx: мок `useRoutes` не включал новый `useSubmitRoute`. Добавлен в мок._

### Completion Notes List

- ✅ Task 1: `Route` interface расширен полями `rejectionReason`, `rejectorUsername`, `rejectedAt`, `approverUsername`, `approvedAt` (nullable/optional)
- ✅ Task 2: Функция `submitForApproval(id)` добавлена в `routesApi.ts` — POST `/api/v1/routes/{id}/submit`
- ✅ Task 3: Hook `useSubmitRoute` добавлен в `useRoutes.ts` — useMutation с инвалидацией кэша и toast-уведомлением
- ✅ Task 4: `RouteDetailsCard` обновлён — submit flow, Modal подтверждения, Alert для pending, блок причины отклонения для rejected
- ✅ Task 5: 10 тестов в `RouteDetailsCard.test.tsx` — все AC покрыты, все проходят
- ✅ Регрессионный прогон: 120/120 тестов прошли (+ 2 давних skipped)

### File List

- `frontend/admin-ui/src/features/routes/types/route.types.ts` — добавлены поля rejection/approval в `Route` interface
- `frontend/admin-ui/src/features/routes/api/routesApi.ts` — добавлена функция `submitForApproval(id)`
- `frontend/admin-ui/src/features/routes/hooks/useRoutes.ts` — добавлен hook `useSubmitRoute`
- `frontend/admin-ui/src/features/routes/components/RouteDetailsCard.tsx` — основной UI submit flow
- `frontend/admin-ui/src/features/routes/components/RouteDetailsCard.test.tsx` — новый файл с 10 тестами
- `frontend/admin-ui/src/features/routes/components/RouteDetailsPage.test.tsx` — добавлен `useSubmitRoute` в мок

## Senior Developer Review (AI)

**Дата:** 2026-02-18
**Ревьюер:** Yury (AI Code Review Workflow)
**Статус:** APPROVED с исправлениями

### Найденные проблемы и исправления

| # | Severity | Проблема | Файл | Статус |
|---|---|---|---|---|
| H2 | MEDIUM | `canEdit` и `canSubmit` — дублирующие переменные с идентичным условием | `RouteDetailsCard.tsx:46-47` | ✅ ИСПРАВЛЕНО — удалён `canEdit`, оба места используют `canSubmit` |
| M2 | MEDIUM | Тест "вызывает API при подтверждении и закрывает modal" не проверял закрытие modal | `RouteDetailsCard.test.tsx:148` | ✅ ИСПРАВЛЕНО — добавлена проверка исчезновения modal-контента |
| M3 | MEDIUM | Хрупкий поиск кнопки через `getAllByRole` + индекс последнего элемента | `RouteDetailsCard.test.tsx:173` | ✅ ИСПРАВЛЕНО — заменено на `getByRole('button', { name: /^отправить$/i })` |
| M4 | MEDIUM | Отсутствовал тест: pending маршрут для не-владельца не показывает Alert | `RouteDetailsCard.test.tsx` | ✅ ИСПРАВЛЕНО — добавлен тест |
| L3 | MEDIUM | Отсутствовал тест: modal остаётся открытым при ошибке submit | `RouteDetailsCard.test.tsx` | ✅ ИСПРАВЛЕНО — добавлен тест |
| — | LOW | `destroyOnClose` deprecated → `destroyOnHidden` | `RouteDetailsCard.tsx:217` | ✅ ИСПРАВЛЕНО |

### Итог
- **Исправлено:** 6 проблем
- **Тестов:** 10 → 12 (добавлены 2 новых теста)
- **Регрессия:** 122/122 passed (2 skipped — давние, не связаны с историей)
- **Все AC реализованы корректно**

## Change Log

- 2026-02-18: Реализована история 4.5 — Submit for Approval UI. Добавлен submit flow в RouteDetailsCard: кнопка отправки для draft, Modal подтверждения, Alert для pending, блок rejection reason для rejected. Добавлены API функция, React Query hook и 10 тестов компонента.
- 2026-02-18: Code Review (AI) — исправлены 6 проблем: удалён дубликат `canEdit`, исправлен хрупкий поиск кнопки в тесте, добавлены 2 новых теста (pending не-владелец, ошибка submit), добавлена верификация закрытия modal, заменён deprecated `destroyOnClose` на `destroyOnHidden`.
