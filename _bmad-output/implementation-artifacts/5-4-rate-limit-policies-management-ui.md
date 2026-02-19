# Story 5.4: Rate Limit Policies Management UI

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As an **Admin**,
I want to manage rate limit policies through the UI,
so that I can configure protection levels easily.

## Acceptance Criteria

**AC1 — Таблица политик Rate Limit:**

**Given** admin пользователь переходит на `/rate-limits`
**When** страница загружается
**Then** отображается таблица со всеми политиками с колонками:
- Name
- Description
- Requests/sec
- Burst Size
- Used By (количество маршрутов, использующих политику)
- Actions (Edit, Delete)

**AC2 — Создание политики через модальное окно:**

**Given** admin нажимает кнопку "New Policy"
**When** модальное окно открывается
**Then** форма отображает поля:
- Name (обязательное, уникальное)
- Description (опциональное)
- Requests per Second (обязательное, число, min 1)
- Burst Size (обязательное, число, >= requests/sec)
**And** валидация предотвращает burst < requests/sec

**AC3 — Успешное создание политики:**

**Given** admin заполняет валидные данные политики и нажимает Submit
**When** создание успешно завершается
**Then** модальное окно закрывается
**And** появляется toast notification: "Policy created"
**And** таблица обновляется с новой политикой

**AC4 — Редактирование политики:**

**Given** admin нажимает "Edit" на политике
**When** модальное окно открывается
**Then** форма заполнена текущими значениями политики
**And** "Save" обновляет политику

**AC5 — Удаление политики с 0 использованиями:**

**Given** admin нажимает "Delete" на политике с usageCount = 0
**When** подтверждение принято
**Then** политика удаляется
**And** появляется toast notification: "Policy deleted"

**AC6 — Запрет удаления используемой политики:**

**Given** admin нажимает "Delete" на политике, используемой N маршрутами
**When** действие выполняется
**Then** появляется error toast: "Cannot delete: policy is used by N routes"
**And** политика НЕ удаляется

**AC7 — Просмотр использующих маршрутов:**

**Given** admin нажимает на число "Used By" (count > 0)
**When** действие срабатывает
**Then** показывается модальное окно или панель со списком маршрутов, использующих эту политику

**AC8 — 403 для non-admin (CUD операции):**

**Given** пользователь с ролью developer или security заходит на `/rate-limits`
**When** страница загружается
**Then** таблица отображается (readonly)
**And** кнопки "New Policy", "Edit", "Delete" НЕ отображаются

## Tasks / Subtasks

- [x] Task 1: Создать API типы для Rate Limits (AC1-AC7)
  - [x] Создать `frontend/admin-ui/src/features/rate-limits/types/rateLimit.types.ts`
  - [x] Типы: RateLimit, CreateRateLimitRequest, UpdateRateLimitRequest
  - [x] Тип RateLimitListResponse для пагинации

- [x] Task 2: Создать API функции для Rate Limits (AC1-AC7)
  - [x] Создать `frontend/admin-ui/src/features/rate-limits/api/rateLimitsApi.ts`
  - [x] Функции: getRateLimits, getRateLimit, createRateLimit, updateRateLimit, deleteRateLimit
  - [x] Функция: getRoutesByRateLimitId (для AC7)

- [x] Task 3: Создать React Query хуки для Rate Limits (AC1-AC7)
  - [x] Создать `frontend/admin-ui/src/features/rate-limits/hooks/useRateLimits.ts`
  - [x] useRateLimits() — список политик
  - [x] useRateLimit(id) — одна политика
  - [x] useCreateRateLimit() — мутация создания
  - [x] useUpdateRateLimit() — мутация обновления
  - [x] useDeleteRateLimit() — мутация удаления
  - [x] useRoutesByRateLimitId(id) — маршруты, использующие политику

- [x] Task 4: Создать RateLimitsTable компонент (AC1, AC8)
  - [x] Создать `frontend/admin-ui/src/features/rate-limits/components/RateLimitsTable.tsx`
  - [x] Колонки: Name, Description, Requests/sec, Burst Size, Used By, Actions
  - [x] "Used By" как кликабельная ссылка для просмотра маршрутов (AC7)
  - [x] Условное отображение кнопок Edit/Delete только для admin (AC8)
  - [x] Пагинация

- [x] Task 5: Создать RateLimitFormModal компонент (AC2, AC3, AC4)
  - [x] Создать `frontend/admin-ui/src/features/rate-limits/components/RateLimitFormModal.tsx`
  - [x] Поля: name, description, requestsPerSecond, burstSize
  - [x] Валидация: name required, requestsPerSecond >= 1, burstSize >= requestsPerSecond
  - [x] Режим создания и редактирования
  - [x] Toast notifications при успехе

- [x] Task 6: Создать RateLimitRoutesModal компонент (AC7)
  - [x] Создать `frontend/admin-ui/src/features/rate-limits/components/RateLimitRoutesModal.tsx`
  - [x] Показывает список маршрутов, использующих политику
  - [x] Ссылки на детали маршрутов

- [x] Task 7: Создать RateLimitsPage компонент (AC1-AC8)
  - [x] Создать `frontend/admin-ui/src/features/rate-limits/components/RateLimitsPage.tsx`
  - [x] Layout с заголовком "Rate Limit Policies"
  - [x] Кнопка "New Policy" (только для admin)
  - [x] Интеграция RateLimitsTable, RateLimitFormModal, RateLimitRoutesModal
  - [x] Обработка удаления с проверкой usageCount (AC5, AC6)

- [x] Task 8: Добавить роут в App.tsx (AC1)
  - [x] Добавить `/rate-limits` роут в маршрутизацию
  - [x] ProtectedRoute с минимальной ролью developer (для просмотра)

- [x] Task 9: Добавить пункт меню в Sidebar (AC1)
  - [x] Добавить "Rate Limits" в боковое меню
  - [x] Иконка: SafetyOutlined (уже присутствовала)

- [x] Task 10: Тесты компонентов (AC1-AC8)
  - [x] Unit тесты для RateLimitsTable
  - [x] Unit тесты для RateLimitFormModal
  - [x] Unit тесты для RateLimitsPage

## Dev Notes

### Существующие паттерны из проекта

**UsersPage.tsx / UserFormModal.tsx** — основной паттерн для страницы с таблицей и модальным окном:
- `useState` для состояния модального окна
- Режим создания (user = null) и редактирования (user = User)
- `useCreateUser` / `useUpdateUser` — React Query мутации
- Form.Item с валидацией
- Toast через message.success / message.error

**RouteForm.tsx** — паттерн валидации:
- Inline валидация полей
- Debounced проверка уникальности
- validateTrigger: ['onChange', 'onBlur']

**RoutesTable.tsx** — паттерн таблицы:
- Ant Design Table с колонками
- Actions как последняя колонка
- Условный рендер по роли пользователя

### Структура файлов для создания

```
frontend/admin-ui/src/features/rate-limits/
├── types/
│   └── rateLimit.types.ts          # СОЗДАТЬ
├── api/
│   └── rateLimitsApi.ts            # СОЗДАТЬ
├── hooks/
│   └── useRateLimits.ts            # СОЗДАТЬ
└── components/
    ├── RateLimitsPage.tsx          # СОЗДАТЬ
    ├── RateLimitsTable.tsx         # СОЗДАТЬ
    ├── RateLimitFormModal.tsx      # СОЗДАТЬ
    └── RateLimitRoutesModal.tsx    # СОЗДАТЬ

frontend/admin-ui/src/
├── App.tsx                         # ИЗМЕНИТЬ — добавить роут
└── layouts/
    └── Sidebar.tsx                 # ИЗМЕНИТЬ — добавить пункт меню
```

### Типы данных

```typescript
// types/rateLimit.types.ts

export interface RateLimit {
  id: string
  name: string
  description: string | null
  requestsPerSecond: number
  burstSize: number
  usageCount: number
  createdBy: string
  createdAt: string
  updatedAt: string
}

export interface RateLimitListResponse {
  items: RateLimit[]
  total: number
  offset: number
  limit: number
}

export interface CreateRateLimitRequest {
  name: string
  description?: string
  requestsPerSecond: number
  burstSize: number
}

export interface UpdateRateLimitRequest {
  name?: string
  description?: string
  requestsPerSecond?: number
  burstSize?: number
}
```

### API функции

```typescript
// api/rateLimitsApi.ts
import axios from '@/shared/utils/axios'
import type { RateLimit, RateLimitListResponse, CreateRateLimitRequest, UpdateRateLimitRequest } from '../types/rateLimit.types'
import type { RouteResponse } from '@/features/routes/types/route.types'

const BASE_URL = '/api/v1/rate-limits'

// Список политик
export const getRateLimits = async (params?: { offset?: number; limit?: number }): Promise<RateLimitListResponse> => {
  const { data } = await axios.get<RateLimitListResponse>(BASE_URL, { params })
  return data
}

// Одна политика
export const getRateLimit = async (id: string): Promise<RateLimit> => {
  const { data } = await axios.get<RateLimit>(`${BASE_URL}/${id}`)
  return data
}

// Создание
export const createRateLimit = async (request: CreateRateLimitRequest): Promise<RateLimit> => {
  const { data } = await axios.post<RateLimit>(BASE_URL, request)
  return data
}

// Обновление
export const updateRateLimit = async (id: string, request: UpdateRateLimitRequest): Promise<RateLimit> => {
  const { data } = await axios.put<RateLimit>(`${BASE_URL}/${id}`, request)
  return data
}

// Удаление
export const deleteRateLimit = async (id: string): Promise<void> => {
  await axios.delete(`${BASE_URL}/${id}`)
}

// Маршруты, использующие политику (для AC7)
export const getRoutesByRateLimitId = async (rateLimitId: string): Promise<RouteResponse[]> => {
  // Используем существующий API с фильтром
  const { data } = await axios.get('/api/v1/routes', { params: { rateLimitId } })
  return data.items
}
```

### React Query хуки

```typescript
// hooks/useRateLimits.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { message } from 'antd'
import * as api from '../api/rateLimitsApi'
import type { CreateRateLimitRequest, UpdateRateLimitRequest } from '../types/rateLimit.types'

const QUERY_KEYS = {
  rateLimits: ['rateLimits'] as const,
  rateLimit: (id: string) => ['rateLimits', id] as const,
  rateLimitRoutes: (id: string) => ['rateLimits', id, 'routes'] as const,
}

// Список политик
export function useRateLimits() {
  return useQuery({
    queryKey: QUERY_KEYS.rateLimits,
    queryFn: () => api.getRateLimits(),
  })
}

// Одна политика
export function useRateLimit(id: string | undefined) {
  return useQuery({
    queryKey: QUERY_KEYS.rateLimit(id!),
    queryFn: () => api.getRateLimit(id!),
    enabled: !!id,
  })
}

// Создание
export function useCreateRateLimit() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data: CreateRateLimitRequest) => api.createRateLimit(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.rateLimits })
      message.success('Policy created')
    },
    onError: (error: Error) => {
      message.error(error.message || 'Не удалось создать политику')
    },
  })
}

// Обновление
export function useUpdateRateLimit() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, data }: { id: string; data: UpdateRateLimitRequest }) =>
      api.updateRateLimit(id, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.rateLimits })
      message.success('Policy updated')
    },
    onError: (error: Error) => {
      message.error(error.message || 'Не удалось обновить политику')
    },
  })
}

// Удаление
export function useDeleteRateLimit() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.deleteRateLimit(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEYS.rateLimits })
      message.success('Policy deleted')
    },
    onError: (error: Error & { response?: { data?: { detail?: string } } }) => {
      // Обработка 409 Conflict (AC6)
      const detail = error.response?.data?.detail
      if (detail && detail.includes('in use')) {
        message.error(detail)
      } else {
        message.error('Не удалось удалить политику')
      }
    },
  })
}

// Маршруты, использующие политику (AC7)
export function useRoutesByRateLimitId(id: string | undefined) {
  return useQuery({
    queryKey: QUERY_KEYS.rateLimitRoutes(id!),
    queryFn: () => api.getRoutesByRateLimitId(id!),
    enabled: !!id,
  })
}
```

### RateLimitFormModal — валидация burstSize >= requestsPerSecond

```tsx
// Кастомная валидация для burstSize
const validateBurstSize = (_: unknown, value: number) => {
  const requestsPerSecond = form.getFieldValue('requestsPerSecond')
  if (value < requestsPerSecond) {
    return Promise.reject(new Error('Burst size must be at least equal to requests per second'))
  }
  return Promise.resolve()
}

<Form.Item
  name="burstSize"
  label="Burst Size"
  dependencies={['requestsPerSecond']}
  rules={[
    { required: true, message: 'Burst size обязателен' },
    { type: 'number', min: 1, message: 'Минимум 1' },
    { validator: validateBurstSize },
  ]}
>
  <InputNumber min={1} style={{ width: '100%' }} />
</Form.Item>
```

### Sidebar — паттерн добавления пункта меню

Посмотреть существующий Sidebar.tsx и добавить:
```tsx
{
  key: 'rate-limits',
  icon: <ThunderboltOutlined />,
  label: <Link to="/rate-limits">Rate Limits</Link>,
}
```

### App.tsx — паттерн роута

```tsx
<Route
  path="/rate-limits"
  element={
    <ProtectedRoute allowedRoles={['developer', 'security', 'admin']}>
      <RateLimitsPage />
    </ProtectedRoute>
  }
/>
```

### Роли и условный рендер (AC8)

```tsx
// В RateLimitsPage.tsx
import { useAuth } from '@/features/auth/context/AuthContext'

function RateLimitsPage() {
  const { user } = useAuth()
  const isAdmin = user?.role === 'admin'

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={3}>Rate Limit Policies</Title>
        {isAdmin && (
          <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
            New Policy
          </Button>
        )}
      </div>
      <RateLimitsTable onEdit={isAdmin ? handleEdit : undefined} onDelete={isAdmin ? handleDelete : undefined} />
    </div>
  )
}
```

### Backend API уже готов

API для rate limits реализован в Story 5.1:
- GET `/api/v1/rate-limits` — список политик с usageCount
- GET `/api/v1/rate-limits/{id}` — одна политика
- POST `/api/v1/rate-limits` — создание (только admin)
- PUT `/api/v1/rate-limits/{id}` — обновление (только admin)
- DELETE `/api/v1/rate-limits/{id}` — удаление (только admin, 409 если используется)

**Важно:** Для AC7 (список маршрутов по rateLimitId) API уже поддерживает фильтр `GET /api/v1/routes?rateLimitId={id}` (реализован в Story 5.2).

### Важные паттерны

1. **Ant Design компоненты:** Table, Modal, Form, Input, InputNumber, Button, message
2. **React Query:** useQuery для данных, useMutation для изменений, queryClient.invalidateQueries после мутаций
3. **Условный рендер по роли:** проверка `user?.role === 'admin'` через useAuth()
4. **Toast notifications:** `message.success()` / `message.error()` из Ant Design
5. **Комментарии на русском:** согласно CLAUDE.md

### Архитектурные требования

- **Комментарии**: только на русском языке
- **Названия тестов**: только на русском языке
- **Ant Design**: использовать как UI библиотеку
- **React Query**: для управления серверным состоянием
- **Axios**: использовать существующий инстанс из `@/shared/utils/axios`
- **TypeScript**: строгая типизация

### Project Structure Notes

- Alignment с unified project structure: `features/rate-limits/` согласно architecture.md
- Detected conflicts: нет

### References

- [Source: planning-artifacts/epics.md#Story-5.4] — Story requirements и AC
- [Source: planning-artifacts/architecture.md#Frontend-Architecture] — Vite, React Query, Ant Design
- [Source: planning-artifacts/ux-design-specification.md] — UX паттерны
- [Source: frontend/admin-ui/src/features/users/components/UsersPage.tsx] — паттерн страницы с таблицей
- [Source: frontend/admin-ui/src/features/users/components/UserFormModal.tsx] — паттерн модального окна формы
- [Source: frontend/admin-ui/src/features/routes/components/RoutesTable.tsx] — паттерн таблицы
- [Source: frontend/admin-ui/src/features/auth/context/AuthContext.tsx] — useAuth для проверки роли
- [Source: implementation-artifacts/5-1-rate-limit-policy-crud-api.md] — Backend API для rate limits
- [Source: implementation-artifacts/5-2-assign-rate-limit-route-api.md] — API с фильтром rateLimitId

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

Нет.

### Completion Notes List

- Реализована полная feature `rate-limits` с типами, API, hooks, и компонентами
- Таблица политик с пагинацией и колонками Name, Description, Requests/sec, Burst Size, Used By, Actions
- Модальное окно создания/редактирования с валидацией burstSize >= requestsPerSecond
- Модальное окно просмотра маршрутов, использующих политику (AC7)
- Условное отображение кнопок Edit/Delete только для admin (AC8)
- Роут `/rate-limits` доступен для developer, security, admin (readonly для non-admin)
- Пункт меню Sidebar уже присутствовал с предыдущих stories
- 37 unit тестов покрывают все AC

### File List

**Созданы:**
- frontend/admin-ui/src/features/rate-limits/types/rateLimit.types.ts
- frontend/admin-ui/src/features/rate-limits/api/rateLimitsApi.ts
- frontend/admin-ui/src/features/rate-limits/hooks/useRateLimits.ts
- frontend/admin-ui/src/features/rate-limits/components/RateLimitsTable.tsx
- frontend/admin-ui/src/features/rate-limits/components/RateLimitFormModal.tsx
- frontend/admin-ui/src/features/rate-limits/components/RateLimitRoutesModal.tsx
- frontend/admin-ui/src/features/rate-limits/components/RateLimitsPage.tsx
- frontend/admin-ui/src/features/rate-limits/components/RateLimitsTable.test.tsx
- frontend/admin-ui/src/features/rate-limits/components/RateLimitFormModal.test.tsx
- frontend/admin-ui/src/features/rate-limits/components/RateLimitsPage.test.tsx
- frontend/admin-ui/src/features/rate-limits/index.ts

**Изменены:**
- frontend/admin-ui/src/App.tsx (добавлен роут /rate-limits с RateLimitsPage)
- frontend/admin-ui/src/features/approval/components/ApprovalsPage.test.tsx (удалена неиспользуемая константа)

### Change Log

- 2026-02-19: Story 5.4 — Rate Limit Policies Management UI полностью реализована
- 2026-02-19: Code Review Fixes — унифицированы сообщения ошибок на английский, заменён deprecated destroyOnClose на destroyOnHidden, добавлены SAFETY комментарии, добавлены тесты для AC5 и AC6
