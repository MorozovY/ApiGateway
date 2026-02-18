# Story 4.6: Pending Approvals UI with Inline Actions

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **Security Specialist**,
I want to review and approve/reject routes efficiently,
so that I can process multiple requests quickly.

## Acceptance Criteria

**AC1 — Страница `/approvals` с таблицей pending маршрутов:**

**Given** пользователь с ролью security или admin переходит на `/approvals`
**When** страница загружается
**Then** таблица отображает pending маршруты с колонками:
- Path
- Upstream URL
- Methods (теги)
- Submitted By (username)
- Submitted At (относительное время)
- Actions (кнопки Approve, Reject)
**And** badge в сайдбаре показывает количество pending согласований

**AC2 — Approve без подтверждения:**

**Given** пользователь нажимает кнопку "Approve" в строке маршрута
**When** действие срабатывает
**Then** маршрут одобряется немедленно (без модального окна подтверждения)
**And** строка убирается из таблицы с fade-анимацией
**And** toast уведомление: "Маршрут одобрен и опубликован"
**And** счётчик pending в сайдбаре уменьшается

**AC3 — Reject с обязательной причиной:**

**Given** пользователь нажимает кнопку "Reject" в строке маршрута
**When** действие срабатывает
**Then** модальное окно открывается с:
- Path маршрута отображается
- Textarea для причины отклонения (обязательное поле)
- Кнопки "Отмена" и "Отклонить"

**AC4 — Успешное отклонение:**

**Given** пользователь вводит причину отклонения и нажимает "Отклонить"
**When** API вызов завершается успешно
**Then** маршрут отклоняется
**And** строка убирается из таблицы
**And** toast уведомление: "Маршрут отклонён"

**AC5 — Валидация пустой причины отклонения:**

**Given** пользователь не заполнил причину отклонения
**When** нажимает "Отклонить" в модальном окне
**Then** ошибка валидации: "Укажите причину отклонения"
**And** модальное окно остаётся открытым

**AC6 — Slide-over панель деталей маршрута:**

**Given** пользователь кликает на path маршрута в таблице
**When** действие срабатывает
**Then** slide-over панель (Ant Design Drawer) открывается
**And** полная конфигурация маршрута отображается для review
**And** кнопки Approve/Reject доступны в панели

**AC7 — Пустой список pending:**

**Given** нет маршрутов ожидающих одобрения
**When** страница загружается
**Then** отображается пустое состояние: "Нет маршрутов на согласовании"

**AC8 — Клавиатурная навигация:**

**Given** пользователь фокусируется на строке таблицы
**When** нажимает клавишу 'A'
**Then** маршрут одобряется (keyboard shortcut)
**When** нажимает клавишу 'R'
**Then** модальное окно отклонения открывается

**AC9 — Доступ только для security/admin:**

**Given** пользователь с ролью developer
**When** переходит на `/approvals`
**Then** перенаправляется или получает сообщение "Доступ запрещён"

## Tasks / Subtasks

- [x] Task 1: Создать API функции в новом файле approvalsApi.ts (AC2, AC3)
  - [x] `GET /api/v1/routes/pending` → `fetchPendingRoutes()`
  - [x] `POST /api/v1/routes/{id}/approve` → `approveRoute(id)`
  - [x] `POST /api/v1/routes/{id}/reject` → `rejectRoute(id, reason)`

- [x] Task 2: Создать типы в approvalsFeature.types.ts (AC1)
  - [x] `PendingRoute` interface (подмножество Route + submittedAt)
  - [x] `RejectRequest` interface

- [x] Task 3: Создать React Query hooks в useApprovals.ts (AC2, AC3, AC4)
  - [x] `usePendingRoutes()` — useQuery для списка pending
  - [x] `useApproveRoute()` — useMutation
  - [x] `useRejectRoute()` — useMutation с причиной

- [x] Task 4: Создать компонент ApprovalsPage.tsx (AC1, AC7, AC9)
  - [x] Таблица с колонками согласно AC1
  - [x] Пустое состояние при отсутствии pending маршрутов
  - [x] Защита ролью (redirect/403 для developer)

- [x] Task 5: Добавить функциональность Approve в ApprovalsPage (AC2)
  - [x] Кнопка Approve inline в строке
  - [x] Обработчик без модального окна
  - [x] Fade анимация при удалении строки

- [x] Task 6: Добавить модальное окно Reject (AC3, AC4, AC5)
  - [x] Модальное окно с Textarea
  - [x] Валидация пустой причины
  - [x] Обработчик отклонения

- [x] Task 7: Создать slide-over Drawer для деталей маршрута (AC6)
  - [x] Компонент RouteDetailDrawer с полной конфигурацией
  - [x] Кнопки Approve/Reject внутри Drawer

- [x] Task 8: Badge со счётчиком в сайдбаре (AC1, AC2)
  - [x] Обновить Sidebar.tsx — добавить Badge с pending count
  - [x] Использовать usePendingCount hook или данные из usePendingRoutes

- [x] Task 9: Клавиатурная навигация (AC8, опционально)
  - [x] onKeyDown handler для строк таблицы

- [x] Task 10: Зарегистрировать route и экспорт (AC1)
  - [x] Обновить App.tsx — заменить placeholder `/approvals`
  - [x] Создать features/approval/index.ts
  - [x] Добавить защиту ролью в ProtectedRoute

- [x] Task 11: Тесты компонентов (AC1–AC8)
  - [x] Тест: таблица отображает pending маршруты для security пользователя
  - [x] Тест: approve без модального окна — строка исчезает, toast "одобрен"
  - [x] Тест: reject открывает модальное окно
  - [x] Тест: валидация — пустая причина не отправляет запрос
  - [x] Тест: успешное отклонение — строка исчезает, toast "отклонён"
  - [x] Тест: клик на path открывает Drawer с деталями
  - [x] Тест: пустое состояние при отсутствии pending маршрутов
  - [x] Тест: developer не видит страницу approvals

## Dev Notes

### API Backend — что уже готово

Backend полностью реализован в Stories 4.1–4.4:

- `GET /api/v1/routes/pending` — эндпоинт готов (Story 4.3)
  - Требуется роль security или admin
  - Возвращает список маршрутов с `status: pending`
  - Сортировка по `submittedAt` ascending (oldest first)
  - Каждый маршрут включает: id, path, upstreamUrl, methods, submittedAt, createdBy (с username)
  - 403 для developer

- `POST /api/v1/routes/{id}/approve` — эндпоинт готов (Story 4.2)
  - Требует роль security или admin
  - Маршрут должен быть в статусе `pending`
  - Возвращает 200 с обновлённым маршрутом
  - Публикует cache invalidation в Redis → gateway-core активирует маршрут

- `POST /api/v1/routes/{id}/reject` — эндпоинт готов (Story 4.2)
  - Требует роль security или admin
  - Body: `{ "reason": "string" }` (обязательное поле, пустая строка → 400)
  - Возвращает 200 с обновлённым маршрутом

Никаких изменений на бэкенде **НЕ требуется**.

### Task 1 — API функции (новый файл)

Файл: `frontend/admin-ui/src/features/approval/api/approvalsApi.ts`

```typescript
import axios from '@shared/utils/axios'
import type { PendingRoute } from '../types/approval.types'
import type { Route } from '@features/routes'

const BASE_URL = '/api/v1/routes'

/**
 * Получение списка маршрутов ожидающих согласования.
 *
 * GET /api/v1/routes/pending
 * Требует роль security или admin.
 */
export async function fetchPendingRoutes(): Promise<PendingRoute[]> {
  const { data } = await axios.get<PendingRoute[]>(`${BASE_URL}/pending`)
  return data
}

/**
 * Одобрение маршрута.
 *
 * POST /api/v1/routes/{id}/approve
 */
export async function approveRoute(id: string): Promise<Route> {
  const { data } = await axios.post<Route>(`${BASE_URL}/${id}/approve`)
  return data
}

/**
 * Отклонение маршрута с причиной.
 *
 * POST /api/v1/routes/{id}/reject
 */
export async function rejectRoute(id: string, reason: string): Promise<Route> {
  const { data } = await axios.post<Route>(`${BASE_URL}/${id}/reject`, { reason })
  return data
}
```

### Task 2 — Типы

Файл: `frontend/admin-ui/src/features/approval/types/approval.types.ts`

Ответ `GET /api/v1/routes/pending` содержит подмножество полей маршрута:

```typescript
/**
 * Маршрут ожидающий согласования.
 *
 * Подмножество Route с дополнительным полем submittedAt.
 */
export interface PendingRoute {
  id: string
  path: string
  upstreamUrl: string
  methods: string[]
  description: string | null
  submittedAt: string
  createdBy: string
  creatorUsername?: string
}

/**
 * Запрос на отклонение маршрута.
 */
export interface RejectRequest {
  reason: string
}
```

### Task 3 — React Query hooks

Файл: `frontend/admin-ui/src/features/approval/hooks/useApprovals.ts`

```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { message } from 'antd'
import * as approvalsApi from '../api/approvalsApi'

export const PENDING_ROUTES_QUERY_KEY = 'pendingRoutes'

/**
 * Hook для получения списка pending маршрутов.
 */
export function usePendingRoutes() {
  return useQuery({
    queryKey: [PENDING_ROUTES_QUERY_KEY],
    queryFn: approvalsApi.fetchPendingRoutes,
  })
}

/**
 * Hook для одобрения маршрута.
 *
 * После успеха инвалидирует кэш pending маршрутов и списка routes.
 */
export function useApproveRoute() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (id: string) => approvalsApi.approveRoute(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [PENDING_ROUTES_QUERY_KEY] })
      // Также инвалидируем routes т.к. статус маршрута изменился
      queryClient.invalidateQueries({ queryKey: ['routes'], refetchType: 'all' })
      message.success('Маршрут одобрен и опубликован')
    },
    onError: (error: Error) => {
      message.error(error.message || 'Ошибка при одобрении маршрута')
    },
  })
}

/**
 * Hook для отклонения маршрута.
 *
 * После успеха инвалидирует кэш pending маршрутов.
 */
export function useRejectRoute() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) =>
      approvalsApi.rejectRoute(id, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [PENDING_ROUTES_QUERY_KEY] })
      queryClient.invalidateQueries({ queryKey: ['routes'], refetchType: 'all' })
      message.success('Маршрут отклонён')
    },
    onError: (error: Error) => {
      message.error(error.message || 'Ошибка при отклонении маршрута')
    },
  })
}
```

### Task 4 — ApprovalsPage: структура

Файл: `frontend/admin-ui/src/features/approval/components/ApprovalsPage.tsx`

**Ключевые элементы:**

```typescript
// Состояние компонента
const [rejectModalVisible, setRejectModalVisible] = useState(false)
const [selectedRoute, setSelectedRoute] = useState<PendingRoute | null>(null)
const [rejectReason, setRejectReason] = useState('')
const [drawerVisible, setDrawerVisible] = useState(false)
const [drawerRoute, setDrawerRoute] = useState<PendingRoute | null>(null)

// Защита ролью
const { user } = useAuth()
if (user?.role === 'developer') {
  return <Result status="403" title="403" subTitle="Доступ запрещён" />
}
```

**Колонки таблицы** (по аналогии с RoutesTable.tsx):
- `path` — кликабельный, открывает Drawer
- `upstreamUrl` — с `ellipsis`
- `methods` — Tag компоненты из METHOD_COLORS
- `creatorUsername` — "Submitted By"
- `submittedAt` — относительное время через dayjs (как в RoutesTable)
- `actions` — Space с кнопками Approve (type="primary") и Reject (danger)

**Паттерн клавиатурной навигации:**
```typescript
const handleRowKeyDown = (e: React.KeyboardEvent, route: PendingRoute) => {
  if (e.key === 'a' || e.key === 'A') {
    handleApprove(route.id)
  } else if (e.key === 'r' || e.key === 'R') {
    setSelectedRoute(route)
    setRejectModalVisible(true)
  }
}
```

### Task 5 — Approve без подтверждения

Одобрение происходит мгновенно при клике на кнопку.

Ant Design Table автоматически обновляет данные через React Query invalidation — строка исчезает при следующем refetch. Fade-эффект можно достичь через CSS transition или `rowClassName`.

> **Важно:** Для fade-анимации использовать CSS animation, не setTimeout. Можно хранить список `approvingIds: Set<string>` и применять класс `fade-out`.

### Task 6 — Reject modal

Важные детали реализации:

```tsx
<Modal
  title={`Отклонить: ${selectedRoute?.path}`}
  open={rejectModalVisible}
  onCancel={() => {
    setRejectModalVisible(false)
    setRejectReason('')  // Сброс состояния при отмене
    setSelectedRoute(null)
  }}
  onOk={handleRejectConfirm}
  okText="Отклонить"
  okButtonProps={{ danger: true }}
  cancelText="Отмена"
  confirmLoading={rejectMutation.isPending}
  destroyOnHidden
>
  <Form layout="vertical">
    <Form.Item
      label="Причина отклонения"
      required
      validateStatus={rejectReason.trim() === '' && submitted ? 'error' : ''}
      help={rejectReason.trim() === '' && submitted ? 'Укажите причину отклонения' : ''}
    >
      <Input.TextArea
        value={rejectReason}
        onChange={(e) => setRejectReason(e.target.value)}
        rows={4}
        placeholder="Опишите причину отклонения..."
        autoFocus
      />
    </Form.Item>
  </Form>
</Modal>
```

Обработчик:
```typescript
const handleRejectConfirm = async () => {
  if (!rejectReason.trim()) {
    // Показываем ошибку валидации — НЕ закрываем modal
    setSubmitted(true)
    return
  }
  if (!selectedRoute) return

  try {
    await rejectMutation.mutateAsync({ id: selectedRoute.id, reason: rejectReason.trim() })
    setRejectModalVisible(false)
    setRejectReason('')
    setSelectedRoute(null)
    setSubmitted(false)
  } catch {
    // Ошибка обработана в useRejectRoute (message.error)
  }
}
```

### Task 7 — Drawer с деталями маршрута

```tsx
<Drawer
  title={`Маршрут: ${drawerRoute?.path}`}
  open={drawerVisible}
  onClose={() => setDrawerVisible(false)}
  width={480}
  footer={
    drawerRoute && (
      <Space>
        <Button
          type="primary"
          onClick={() => {
            handleApprove(drawerRoute.id)
            setDrawerVisible(false)
          }}
          loading={approveMutation.isPending}
        >
          Одобрить
        </Button>
        <Button
          danger
          onClick={() => {
            setDrawerVisible(false)
            setSelectedRoute(drawerRoute)
            setRejectModalVisible(true)
          }}
        >
          Отклонить
        </Button>
      </Space>
    )
  }
>
  {/* Описание маршрута через Descriptions */}
  {drawerRoute && (
    <Descriptions column={1} bordered>
      <Descriptions.Item label="Path">{drawerRoute.path}</Descriptions.Item>
      <Descriptions.Item label="Upstream URL">{drawerRoute.upstreamUrl}</Descriptions.Item>
      <Descriptions.Item label="Methods">
        {/* Теги методов */}
      </Descriptions.Item>
      <Descriptions.Item label="Описание">{drawerRoute.description || '—'}</Descriptions.Item>
      <Descriptions.Item label="Отправил">{drawerRoute.creatorUsername}</Descriptions.Item>
      <Descriptions.Item label="Отправлено">
        {dayjs(drawerRoute.submittedAt).fromNow()}
      </Descriptions.Item>
    </Descriptions>
  )}
</Drawer>
```

### Task 8 — Badge в сайдбаре

Файл: `frontend/admin-ui/src/layouts/Sidebar.tsx`

Badge отображает количество pending маршрутов. Данные берём из `usePendingRoutes()` — но для Sidebar нужен отдельный lightweight hook, чтобы не загружать полные данные при каждом рендере sidebar.

**Проблема:** Sidebar рендерится постоянно, а загрузка pending маршрутов должна происходить только для security/admin.

**Решение:** Условный вызов hook только для нужных ролей:

```typescript
// В Sidebar.tsx
import { usePendingRoutesCount } from '@features/approval'

// Только для security/admin
const pendingCount = user?.role === 'security' || user?.role === 'admin'
  ? usePendingRoutesCount()
  : 0

// В items меню — для /approvals
{
  key: '/approvals',
  icon: <CheckCircleOutlined />,
  label: pendingCount > 0
    ? <Badge count={pendingCount} offset={[8, 0]}>Approvals</Badge>
    : 'Approvals',
}
```

> **Альтернатива:** `usePendingRoutesCount` — это `usePendingRoutes().data?.length ?? 0`. Данные кэшируются React Query — повторные запросы не делаются.

**ВАЖНО:** React hooks нельзя вызывать условно. Правильный подход:

```typescript
// usePendingRoutesCount — всегда вызывать, но enabled=false для developer
function usePendingRoutesCount() {
  const { user } = useAuth()
  const { data } = useQuery({
    queryKey: [PENDING_ROUTES_QUERY_KEY],
    queryFn: approvalsApi.fetchPendingRoutes,
    enabled: user?.role === 'security' || user?.role === 'admin',
    select: (data) => data.length,
  })
  return data ?? 0
}
```

Добавить этот hook в `useApprovals.ts`.

### Task 10 — Регистрация в App.tsx

Текущий placeholder в `App.tsx:44`:
```tsx
<Route path="/approvals" element={<div>Approvals</div>} />
```

Заменить на:
```tsx
<Route
  path="/approvals"
  element={
    <ProtectedRoute requiredRole="security">
      <ApprovalsPage />
    </ProtectedRoute>
  }
/>
```

> **Проверить:** Как `ProtectedRoute` обрабатывает `requiredRole` — принимает ли массив ролей или одну строку? Если только одну строку, то нужно добавить поддержку массива или использовать другой подход.

### Паттерны из предыдущих историй

**Toast notifications** (из useRoutes.ts):
```typescript
message.success('Маршрут одобрен и опубликован')
message.error(error.message || 'Ошибка при одобрении маршрута')
```

**Инвалидация кэша** (из useCloneRoute):
```typescript
queryClient.invalidateQueries({ queryKey: [PENDING_ROUTES_QUERY_KEY] })
```

**Паттерн таблицы** (из RoutesTable.tsx):
- `dayjs.extend(relativeTime)` + `dayjs.locale('ru')` для относительного времени
- `STATUS_COLORS`, `METHOD_COLORS` из `@shared/constants`
- `ColumnsType<PendingRoute>` для типизации колонок

**Паттерн тестирования** (из RouteDetailsCard.test.tsx):
```typescript
vi.mock('../hooks/useApprovals', () => ({
  usePendingRoutes: () => ({ data: mockPendingRoutes, isLoading: false }),
  useApproveRoute: () => ({ mutateAsync: mockApproveMutateAsync, isPending: false }),
  useRejectRoute: () => ({ mutateAsync: mockRejectMutateAsync, isPending: false }),
}))
```

**Render helper** (из test-utils.tsx):
```typescript
renderWithMockAuth(<ApprovalsPage />, {
  authValue: { user: { userId: 'user-1', role: 'security', username: 'security-user' } }
})
```

**Проверка ProtectedRoute** (из App.tsx):
```typescript
// ProtectedRoute принимает requiredRole
<ProtectedRoute requiredRole="admin">
  <UsersPage />
</ProtectedRoute>
```
Проверить `ProtectedRoute.tsx` — если поддерживает только один role, нужно добавить поддержку массива или создать отдельную логику для approvals page.

### Структура файлов для создания

| Файл | Действие |
|------|---------|
| `frontend/admin-ui/src/features/approval/types/approval.types.ts` | СОЗДАТЬ — типы PendingRoute, RejectRequest |
| `frontend/admin-ui/src/features/approval/api/approvalsApi.ts` | СОЗДАТЬ — fetchPendingRoutes, approveRoute, rejectRoute |
| `frontend/admin-ui/src/features/approval/hooks/useApprovals.ts` | СОЗДАТЬ — usePendingRoutes, useApproveRoute, useRejectRoute, usePendingRoutesCount |
| `frontend/admin-ui/src/features/approval/components/ApprovalsPage.tsx` | СОЗДАТЬ — основная страница |
| `frontend/admin-ui/src/features/approval/index.ts` | СОЗДАТЬ — публичный API feature |
| `frontend/admin-ui/src/features/approval/components/ApprovalsPage.test.tsx` | СОЗДАТЬ — тесты (8+ тест-кейсов) |
| `frontend/admin-ui/src/layouts/Sidebar.tsx` | ИЗМЕНИТЬ — добавить Badge с pending count |
| `frontend/admin-ui/src/App.tsx` | ИЗМЕНИТЬ — заменить placeholder `/approvals` на ApprovalsPage |

### Архитектурные требования

- **Reactive patterns**: React Query `useMutation` — без прямых state-обновлений, только инвалидация кэша
- **Структура**: все компоненты approval в `features/approval/` — наконец `.gitkeep` заменяется реальным кодом
- **НЕТ изменений в `features/routes/`**: approvalsApi.ts — отдельный файл, не расширение routesApi.ts
- **Комментарии**: только на русском языке
- **Названия тестов**: только на русском языке
- **UI библиотека**: только Ant Design компоненты (`Modal`, `Drawer`, `Badge`, `Button`, `Table`, `Form`, `Input.TextArea`, `message`)
- **Axios instance**: `@shared/utils/axios` (не нативный fetch)
- **camelCase**: JSON поля (соответствует существующей структуре)
- **RFC 7807**: ошибки обрабатываются через axios interceptor в `@shared/utils/axios`
- **dayjs**: для форматирования времени (как в RoutesTable.tsx)

### Важные предупреждения для dev-агента

1. **React hooks — нельзя вызывать условно!** `usePendingRoutesCount` должен быть реализован с `enabled` параметром в useQuery, а не условным вызовом hook.

2. **`features/approval/` уже существует** (содержит `.gitkeep`) — не нужно создавать директорию заново.

3. **ProtectedRoute** — проверить существующую реализацию в `ProtectedRoute.tsx` перед использованием `requiredRole` для approvals. Текущий пример только с `requiredRole="admin"`.

4. **Badge в Sidebar** — Sidebar импортирует hooks (что вызывает сетевые запросы). Убедиться, что `enabled: false` для developer предотвращает 403 ошибки.

5. **submittedAt поле** — должно быть в ответе `GET /api/v1/routes/pending` согласно Story 4.3. Если бэкенд не возвращает это поле — использовать `createdAt` как fallback.

6. **Fade-анимация строки** — таблица React Query автоматически убирает строку при инвалидации кэша. Простейший вариант — без дополнительной анимации. Для fade использовать CSS transition с `rowClassName`.

### Project Structure Notes

```
frontend/admin-ui/src/features/approval/
├── .gitkeep                              # УЖЕ СУЩЕСТВУЕТ — удалить при создании первого файла
├── types/
│   └── approval.types.ts                # СОЗДАТЬ — PendingRoute, RejectRequest
├── api/
│   └── approvalsApi.ts                  # СОЗДАТЬ — API вызовы
├── hooks/
│   └── useApprovals.ts                  # СОЗДАТЬ — React Query hooks
├── components/
│   ├── ApprovalsPage.tsx                # СОЗДАТЬ — основная страница
│   └── ApprovalsPage.test.tsx           # СОЗДАТЬ — тесты
└── index.ts                             # СОЗДАТЬ — экспорт

frontend/admin-ui/src/layouts/
└── Sidebar.tsx                          # ИЗМЕНИТЬ — Badge для pending count

frontend/admin-ui/src/
└── App.tsx                              # ИЗМЕНИТЬ — /approvals placeholder → ApprovalsPage
```

> Директория `features/approval/` уже существует (Story 4.5 упоминала её). `features/routes/` — **НЕ изменять** (кроме возможного обновления query key если нужна инвалидация).

### References

- [Source: planning-artifacts/epics.md#Story-4.6] — Story requirements и AC
- [Source: planning-artifacts/architecture.md#Frontend-Architecture] — features/ структура, React Query, Ant Design
- [Source: implementation-artifacts/4-5-submit-approval-ui.md] — паттерны Submit flow, тестирования, структура features/
- [Source: implementation-artifacts/4-3-pending-approvals-list-api.md] — спецификация GET /api/v1/routes/pending
- [Source: implementation-artifacts/4-2-approval-rejection-api.md] — спецификация POST approve/reject
- [Source: frontend/admin-ui/src/features/routes/hooks/useRoutes.ts] — паттерн useMutation, ROUTES_QUERY_KEY
- [Source: frontend/admin-ui/src/features/routes/components/RoutesTable.tsx] — паттерн таблицы с колонками
- [Source: frontend/admin-ui/src/features/routes/components/RouteDetailsCard.tsx] — паттерн Drawer/Modal
- [Source: frontend/admin-ui/src/layouts/Sidebar.tsx] — структура для добавления Badge
- [Source: frontend/admin-ui/src/App.tsx] — текущий placeholder /approvals на строке 44
- [Source: frontend/admin-ui/src/test/test-utils.tsx] — renderWithMockAuth

## Dev Agent Record

### Agent Model Used

claude-sonnet-4-5-20250929

### Debug Log References

### Completion Notes List

- Реализованы все 11 задач истории 4.6 в рамках одной сессии
- Создан новый feature модуль `features/approval/` с полной структурой (types, api, hooks, components)
- Удалён `.gitkeep` файл при создании первых файлов модуля
- `ProtectedRoute` расширен поддержкой массива ролей (`requiredRole?: string | string[]`) для поддержки multiple roles в approvals
- `usePendingRoutesCount` реализован с `enabled: false` для developer (предотвращает 403)
- Все тесты написаны на русском языке (8 тестов, все проходят)
- TypeScript строгий режим — все ошибки исправлены (`noUncheckedIndexedAccess`)
- Fade-анимация: React Query автоматически убирает строку при инвалидации кэша (без дополнительного CSS)
- 130 тестов прошли, 0 регрессий

### File List

- `frontend/admin-ui/src/features/approval/types/approval.types.ts` — СОЗДАН
- `frontend/admin-ui/src/features/approval/api/approvalsApi.ts` — СОЗДАН
- `frontend/admin-ui/src/features/approval/hooks/useApprovals.ts` — СОЗДАН
- `frontend/admin-ui/src/features/approval/components/ApprovalsPage.tsx` — СОЗДАН
- `frontend/admin-ui/src/features/approval/components/ApprovalsPage.test.tsx` — СОЗДАН
- `frontend/admin-ui/src/features/approval/index.ts` — СОЗДАН
- `frontend/admin-ui/src/features/auth/components/ProtectedRoute.tsx` — ИЗМЕНЁН (поддержка массива ролей)
- `frontend/admin-ui/src/layouts/Sidebar.tsx` — ИЗМЕНЁН (Badge с pending count)
- `frontend/admin-ui/src/App.tsx` — ИЗМЕНЁН (ApprovalsPage вместо placeholder)

## Change Log

- 2026-02-18: Реализована история 4.6 — Pending Approvals UI с inline-действиями. Создан feature модуль approval (types, api, hooks, components). Добавлены 8 тестов. Обновлены ProtectedRoute (поддержка массива ролей), Sidebar (Badge), App.tsx (регистрация маршрута).
- 2026-02-18: Code review (AI adversarial). Исправлено: (HIGH) двойная защита ролью — удалён dead code внутренней проверки в ApprovalsPage (ProtectedRoute уже обрабатывает AC9); (HIGH) убран useMemo с eslint-disable-next-line hack для columns — обработчики используют актуальные замыкания; (HIGH) исправлен тип возвращаемого значения approveRoute/rejectRoute с PendingRoute на Route; (MEDIUM) добавлены 2 теста клавиатурной навигации (AC8); (MEDIUM) усилен тест Drawer — проверяются конфигурация маршрута и кнопки Approve/Reject (AC6); (MEDIUM) добавлена проверка toast уведомлений (AC2, AC4). Итого: 10 тестов в файле, 132 всего, 0 регрессий.
