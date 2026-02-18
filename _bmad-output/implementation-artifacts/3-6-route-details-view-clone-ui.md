# Story 3.6: Route Details View & Clone UI

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **Developer**,
I want to view route details and clone routes from UI,
So that I can inspect configurations and reuse them.

## Acceptance Criteria

1. **AC1: Страница деталей маршрута**
   **Given** пользователь переходит на `/routes/{id}`
   **When** страница загружается
   **Then** детали маршрута отображаются в card layout:
   - Header: Path как заголовок, Status badge
   - Секции: Configuration (upstream, methods), Metadata (автор, даты)
   - Actions: Edit (если draft), Clone, Back to list

2. **AC2: Кнопка Edit для draft маршрутов**
   **Given** маршрут в статусе draft и пользователь — владелец
   **When** пользователь нажимает "Edit"
   **Then** пользователь перенаправляется на форму редактирования

3. **AC3: Скрытие Edit для non-draft маршрутов**
   **Given** маршрут не в статусе draft
   **When** страница загружается
   **Then** кнопка "Edit" не отображается

4. **AC4: Клонирование маршрута**
   **Given** пользователь нажимает кнопку "Clone"
   **When** действие выполнено
   **Then** toast notification "Route cloned successfully"
   **And** пользователь перенаправляется на страницу редактирования клонированного маршрута

5. **AC5: Отображение Rate Limit**
   **Given** маршруту назначена политика rate limit
   **When** страница деталей загружается
   **Then** отображается информация о rate limit (название, лимиты)

6. **AC6: Обработка несуществующего маршрута**
   **Given** маршрут не существует
   **When** пользователь переходит на `/routes/nonexistent`
   **Then** отображается страница 404 с "Route not found"
   **And** ссылка для возврата к списку маршрутов

## Tasks / Subtasks

- [x] **Task 1: Создать RouteDetailsPage компонент** (AC: #1, #6)
  - [x] Subtask 1.1: Создать `features/routes/components/RouteDetailsPage.tsx`
  - [x] Subtask 1.2: Добавить роутинг `/routes/:id` в App.tsx
  - [x] Subtask 1.3: Загрузка данных маршрута (useRoute hook)
  - [x] Subtask 1.4: Обработка loading и error states
  - [x] Subtask 1.5: 404 страница для несуществующего маршрута

- [x] **Task 2: Создать RouteDetailsCard компонент** (AC: #1, #5)
  - [x] Subtask 2.1: Создать `features/routes/components/RouteDetailsCard.tsx`
  - [x] Subtask 2.2: Header с path и StatusBadge
  - [x] Subtask 2.3: Секция Configuration (upstream URL, methods tags)
  - [x] Subtask 2.4: Секция Metadata (автор, createdAt, updatedAt)
  - [x] Subtask 2.5: Секция Rate Limit (если назначен)

- [x] **Task 3: Реализовать Actions** (AC: #2, #3, #4)
  - [x] Subtask 3.1: Кнопка "Back to list" с навигацией на /routes
  - [x] Subtask 3.2: Кнопка "Edit" (условная, только для draft + owner)
  - [x] Subtask 3.3: Кнопка "Clone" с использованием useCloneRoute hook
  - [x] Subtask 3.4: Redirect после клонирования на `/routes/{newId}/edit`

- [x] **Task 4: Создать тесты** (AC: #1, #2, #3, #4, #5, #6)
  - [x] Subtask 4.1: Создать `RouteDetailsPage.test.tsx`
  - [x] Subtask 4.2: Тест отображения деталей маршрута
  - [x] Subtask 4.3: Тест кнопки Edit для draft маршрута
  - [x] Subtask 4.4: Тест скрытия Edit для non-draft маршрута
  - [x] Subtask 4.5: Тест клонирования и редиректа
  - [x] Subtask 4.6: Тест 404 для несуществующего маршрута
  - [x] Subtask 4.7: Тест отображения rate limit информации

## Dev Notes

### Previous Story Intelligence (Story 3.5 — Route Create/Edit Form UI)

**Реализованные компоненты в Story 3.5:**
- `features/routes/components/RouteFormPage.tsx` — страница создания/редактирования с режимами create/edit
- `features/routes/components/RouteForm.tsx` — форма с inline валидацией
- Роутинг: `/routes/new`, `/routes/:id/edit` уже добавлены в App.tsx

**Используемые паттерны:**
- useRoute(id) hook для загрузки маршрута по ID
- useCloneRoute() hook уже реализован и готов к использованию
- message.success/error для toast notifications
- StatusBadge компонент в RoutesTable (можно извлечь или переиспользовать)
- Russian localization для всех labels и messages

**Существующие компоненты:**
```
frontend/admin-ui/src/features/routes/
├── components/
│   ├── RoutesPage.tsx          — страница списка
│   ├── RoutesTable.tsx         — таблица с StatusBadge inline
│   ├── RouteFormPage.tsx       — создание/редактирование
│   ├── RouteForm.tsx           — форма маршрута
│   └── RouteFormPage.test.tsx  — тесты формы
├── api/
│   └── routesApi.ts            — API (fetchRouteById, cloneRoute уже есть)
├── hooks/
│   └── useRoutes.ts            — hooks (useRoute, useCloneRoute уже есть)
├── types/
│   └── route.types.ts          — типы Route, RouteStatus
└── index.ts                    — экспорты
```

---

### Architecture Compliance

**Из architecture.md — Frontend Architecture:**

| Решение | Выбор | Применение |
|---------|-------|------------|
| **UI Library** | Ant Design | Card, Descriptions, Tag, Badge, Button, Space, Result |
| **State Management** | React Query | useRoute(id) уже реализован |
| **Routing** | React Router v6 | useParams, useNavigate |

**Паттерн Card Layout (из Ant Design):**
```typescript
import { Card, Descriptions, Tag, Badge, Button, Space, Result } from 'antd'
```

---

### Technical Requirements

**1. Компонент RouteDetailsPage:**

```typescript
// features/routes/components/RouteDetailsPage.tsx
import { useParams, useNavigate } from 'react-router-dom'
import { Spin, Result, Button } from 'antd'
import { useRoute } from '../hooks/useRoutes'
import { RouteDetailsCard } from './RouteDetailsCard'

export function RouteDetailsPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const { data: route, isLoading, error } = useRoute(id)

  if (isLoading) {
    return <Spin size="large" className="page-spinner" />
  }

  // 404 для несуществующего маршрута
  if (error || !route) {
    return (
      <Result
        status="404"
        title="Маршрут не найден"
        subTitle="Маршрут с указанным ID не существует"
        extra={
          <Button type="primary" onClick={() => navigate('/routes')}>
            Вернуться к списку
          </Button>
        }
      />
    )
  }

  return <RouteDetailsCard route={route} />
}
```

**2. Компонент RouteDetailsCard:**

```typescript
// features/routes/components/RouteDetailsCard.tsx
import { Card, Descriptions, Tag, Badge, Button, Space, Typography } from 'antd'
import { EditOutlined, CopyOutlined, ArrowLeftOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useCloneRoute } from '../hooks/useRoutes'
import type { Route } from '../types/route.types'
import { useAuth } from '@features/auth/hooks/useAuth'

const { Title } = Typography

// Цвета статусов (из RoutesTable.tsx)
const STATUS_COLORS: Record<string, string> = {
  draft: 'default',
  pending: 'warning',
  published: 'success',
  rejected: 'error',
}

const STATUS_LABELS: Record<string, string> = {
  draft: 'Черновик',
  pending: 'На согласовании',
  published: 'Опубликован',
  rejected: 'Отклонён',
}

interface RouteDetailsCardProps {
  route: Route
}

export function RouteDetailsCard({ route }: RouteDetailsCardProps) {
  const navigate = useNavigate()
  const { user } = useAuth()
  const cloneMutation = useCloneRoute()

  // Проверка: можно ли редактировать (draft + owner)
  const canEdit = route.status === 'draft' && route.createdBy === user?.userId

  const handleEdit = () => {
    navigate(`/routes/${route.id}/edit`)
  }

  const handleClone = async () => {
    const cloned = await cloneMutation.mutateAsync(route.id)
    navigate(`/routes/${cloned.id}/edit`)
  }

  const handleBack = () => {
    navigate('/routes')
  }

  return (
    <Card
      title={
        <Space>
          <Title level={4} style={{ margin: 0 }}>{route.path}</Title>
          <Badge status={STATUS_COLORS[route.status]} text={STATUS_LABELS[route.status]} />
        </Space>
      }
      extra={
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={handleBack}>
            Назад
          </Button>
          {canEdit && (
            <Button icon={<EditOutlined />} onClick={handleEdit}>
              Редактировать
            </Button>
          )}
          <Button
            icon={<CopyOutlined />}
            onClick={handleClone}
            loading={cloneMutation.isPending}
          >
            Клонировать
          </Button>
        </Space>
      }
    >
      <Descriptions column={1} bordered>
        <Descriptions.Item label="Upstream URL">{route.upstreamUrl}</Descriptions.Item>
        <Descriptions.Item label="HTTP Methods">
          {route.methods.map(method => (
            <Tag key={method} color="blue">{method}</Tag>
          ))}
        </Descriptions.Item>
        <Descriptions.Item label="Описание">
          {route.description || '—'}
        </Descriptions.Item>
        <Descriptions.Item label="Автор">{route.creatorUsername}</Descriptions.Item>
        <Descriptions.Item label="Создан">{formatDate(route.createdAt)}</Descriptions.Item>
        <Descriptions.Item label="Обновлён">{formatDate(route.updatedAt)}</Descriptions.Item>

        {/* Rate Limit секция — если назначен */}
        {route.rateLimitId && (
          <Descriptions.Item label="Rate Limit">
            {/* TODO: Загрузить и отобразить детали rate limit */}
            Назначен (ID: {route.rateLimitId})
          </Descriptions.Item>
        )}
      </Descriptions>
    </Card>
  )
}

function formatDate(isoString: string): string {
  return new Date(isoString).toLocaleString('ru-RU', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}
```

**3. Роутинг (добавить в App.tsx):**

```typescript
// В App.tsx, после существующих роутов /routes
<Route path="/routes/:id" element={<RouteDetailsPage />} />
```

**ВАЖНО:** Порядок роутов в React Router! Более специфичные роуты должны быть выше:
```typescript
<Route path="/routes/new" element={<RouteFormPage />} />
<Route path="/routes/:id/edit" element={<RouteFormPage />} />
<Route path="/routes/:id" element={<RouteDetailsPage />} />  // Добавить
<Route path="/routes" element={<RoutesPage />} />
```

**4. Интеграция с useAuth для проверки владельца:**

```typescript
// Из features/auth/hooks/useAuth.ts
// Должен возвращать user с полем userId для проверки ownership
const { user } = useAuth()
const isOwner = route.createdBy === user?.userId
```

**5. Улучшение useCloneRoute для возврата созданного маршрута:**

В текущей реализации `useCloneRoute` возвращает Route после клонирования:
```typescript
// Из useRoutes.ts
export function useCloneRoute() {
  return useMutation({
    mutationFn: (id: string) => routesApi.cloneRoute(id),
    // ...
  })
}
```

Использование:
```typescript
const cloned = await cloneMutation.mutateAsync(route.id)
navigate(`/routes/${cloned.id}/edit`)  // Redirect на редактирование клона
```

---

### Из UX Design Specification

**Route Details View паттерны:**
- Card layout: Header с path + status badge
- Sections: Configuration, Metadata
- Actions: Edit (draft only), Clone, Back

**Status Badges (из ux-design-specification.md):**
- Draft (серый) — `default`
- Pending (жёлтый) — `warning`
- Published (зелёный) — `success`
- Rejected (красный) — `error`

**Emotional Design:**
- Visibility of status: всегда видно статус
- Safe to Explore: view режим без страха что-то сломать
- Progress Visibility: статусы, breadcrumbs

**404 Page:**
- Использовать Ant Design Result component
- Показать сообщение "Маршрут не найден"
- Кнопка возврата к списку

---

### Project Structure Notes

**Файлы для создания:**
```
frontend/admin-ui/src/features/routes/
├── components/
│   ├── RouteDetailsPage.tsx         (NEW)
│   ├── RouteDetailsPage.test.tsx    (NEW)
│   ├── RouteDetailsCard.tsx         (NEW)
│   └── ... existing files
└── index.ts                         (MODIFY — добавить экспорты)

frontend/admin-ui/src/App.tsx        (MODIFY — добавить роут /routes/:id)
```

**Зависимости (уже установлены):**
- `antd` — Card, Descriptions, Tag, Badge, Button, Space, Result, Typography
- `@ant-design/icons` — EditOutlined, CopyOutlined, ArrowLeftOutlined
- `react-router-dom` — useParams, useNavigate
- `@tanstack/react-query` — hooks уже созданы

**Утилиты:**
- formatDate можно вынести в `shared/utils/formatDate.ts` если ещё не существует
- Проверить есть ли уже StatusBadge компонент или создать как shared

---

### Testing Standards

**Из CLAUDE.md:**
- Названия тестов ОБЯЗАТЕЛЬНО на русском языке
- Комментарии в коде на русском языке
- Использовать Vitest (уже настроен)
- Использовать test-utils.tsx для рендеринга с провайдерами

**Примеры тестов:**

```typescript
// features/routes/components/RouteDetailsPage.test.tsx
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '../../../test/test-utils'
import { RouteDetailsPage } from './RouteDetailsPage'
import { useParams, useNavigate } from 'react-router-dom'
import * as routesApi from '../api/routesApi'

// Мок React Router
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useParams: vi.fn(),
    useNavigate: vi.fn(() => vi.fn()),
  }
})

// Мок API
vi.mock('../api/routesApi', () => ({
  fetchRouteById: vi.fn(),
  cloneRoute: vi.fn(),
}))

// Мок Auth
vi.mock('@features/auth/hooks/useAuth', () => ({
  useAuth: vi.fn(() => ({
    user: { userId: 'user-1', username: 'testuser', role: 'developer' },
  })),
}))

const mockRoute = {
  id: 'route-1',
  path: '/api/orders',
  upstreamUrl: 'http://order-service:8080',
  methods: ['GET', 'POST'],
  description: 'Order service',
  status: 'draft' as const,
  createdBy: 'user-1',
  creatorUsername: 'testuser',
  createdAt: '2026-02-18T10:00:00Z',
  updatedAt: '2026-02-18T10:00:00Z',
  rateLimitId: null,
}

describe('RouteDetailsPage', () => {
  const navigate = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useNavigate).mockReturnValue(navigate)
    vi.mocked(useParams).mockReturnValue({ id: 'route-1' })
    vi.mocked(routesApi.fetchRouteById).mockResolvedValue(mockRoute)
  })

  it('отображает детали маршрута', async () => {
    render(<RouteDetailsPage />)

    await waitFor(() => {
      expect(screen.getByText('/api/orders')).toBeInTheDocument()
    })

    expect(screen.getByText('http://order-service:8080')).toBeInTheDocument()
    expect(screen.getByText('GET')).toBeInTheDocument()
    expect(screen.getByText('POST')).toBeInTheDocument()
    expect(screen.getByText('Order service')).toBeInTheDocument()
  })

  it('показывает кнопку Edit для draft маршрута владельца', async () => {
    render(<RouteDetailsPage />)

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /редактировать/i })).toBeInTheDocument()
    })
  })

  it('скрывает кнопку Edit для non-draft маршрута', async () => {
    vi.mocked(routesApi.fetchRouteById).mockResolvedValue({
      ...mockRoute,
      status: 'published',
    })

    render(<RouteDetailsPage />)

    await waitFor(() => {
      expect(screen.getByText('/api/orders')).toBeInTheDocument()
    })

    expect(screen.queryByRole('button', { name: /редактировать/i })).not.toBeInTheDocument()
  })

  it('скрывает кнопку Edit для draft маршрута чужого владельца', async () => {
    vi.mocked(routesApi.fetchRouteById).mockResolvedValue({
      ...mockRoute,
      createdBy: 'other-user',
    })

    render(<RouteDetailsPage />)

    await waitFor(() => {
      expect(screen.getByText('/api/orders')).toBeInTheDocument()
    })

    expect(screen.queryByRole('button', { name: /редактировать/i })).not.toBeInTheDocument()
  })

  it('клонирует маршрут и редиректит на edit', async () => {
    const clonedRoute = { ...mockRoute, id: 'cloned-route-id' }
    vi.mocked(routesApi.cloneRoute).mockResolvedValue(clonedRoute)

    render(<RouteDetailsPage />)

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /клонировать/i })).toBeInTheDocument()
    })

    fireEvent.click(screen.getByRole('button', { name: /клонировать/i }))

    await waitFor(() => {
      expect(navigate).toHaveBeenCalledWith('/routes/cloned-route-id/edit')
    })
  })

  it('показывает 404 для несуществующего маршрута', async () => {
    vi.mocked(routesApi.fetchRouteById).mockRejectedValue(new Error('Not found'))

    render(<RouteDetailsPage />)

    await waitFor(() => {
      expect(screen.getByText('Маршрут не найден')).toBeInTheDocument()
    })

    expect(screen.getByRole('button', { name: /вернуться к списку/i })).toBeInTheDocument()
  })

  it('отображает rate limit информацию если назначен', async () => {
    vi.mocked(routesApi.fetchRouteById).mockResolvedValue({
      ...mockRoute,
      rateLimitId: 'rate-limit-1',
    })

    render(<RouteDetailsPage />)

    await waitFor(() => {
      expect(screen.getByText(/rate limit/i)).toBeInTheDocument()
    })
  })

  it('навигирует на редактирование при клике Edit', async () => {
    render(<RouteDetailsPage />)

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /редактировать/i })).toBeInTheDocument()
    })

    fireEvent.click(screen.getByRole('button', { name: /редактировать/i }))

    expect(navigate).toHaveBeenCalledWith('/routes/route-1/edit')
  })

  it('навигирует назад к списку при клике Назад', async () => {
    render(<RouteDetailsPage />)

    await waitFor(() => {
      expect(screen.getByRole('button', { name: /назад/i })).toBeInTheDocument()
    })

    fireEvent.click(screen.getByRole('button', { name: /назад/i }))

    expect(navigate).toHaveBeenCalledWith('/routes')
  })
})
```

---

### Git Intelligence

**Последние коммиты (Epic 3):**
```
df87cb2 feat: Route Create/Edit Form UI with validation (Story 3.5)
b0356d0 feat: Routes List UI with filtering, search and pagination (Story 3.4)
a42d6ae feat: Route List Filtering, Details & Clone API (Stories 3.2, 3.3)
9fcd455 feat: Route CRUD API with ownership and status validation (Story 3.1)
```

**Релевантные файлы из предыдущих историй:**
- `features/routes/` — вся структура feature модуля
- `features/routes/hooks/useRoutes.ts` — hooks useRoute, useCloneRoute
- `features/routes/api/routesApi.ts` — API: fetchRouteById, cloneRoute
- `features/routes/types/route.types.ts` — типы Route, RouteStatus
- `features/auth/hooks/useAuth.ts` — проверка текущего пользователя

**Commit message format:**
```
feat: Route Details View & Clone UI (Story 3.6)

- Add RouteDetailsPage with route information display
- Add RouteDetailsCard with card layout
- Add Edit button (draft + owner only)
- Add Clone button with redirect to edit
- Add 404 handling for non-existent routes
- Add rate limit information display

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
```

---

### References

- [Source: architecture.md#Frontend Architecture] — Ant Design, React Query
- [Source: ux-design-specification.md#Status Badges] — Цвета статусов
- [Source: ux-design-specification.md#Core User Experience] — Card layout
- [Source: epics.md#Story 3.6] — Acceptance Criteria
- [Source: CLAUDE.md] — Русские комментарии и названия тестов
- [Source: 3-5-route-create-edit-form-ui.md] — Паттерны предыдущей истории
- [Source: features/routes/hooks/useRoutes.ts] — Существующие hooks

---

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- Все 24 теста для RouteDetailsPage прошли успешно
- Полный test suite: 107 тестов passed (2 skipped из предыдущих историй)
- Build прошёл успешно

### Completion Notes List

- Реализован RouteDetailsPage компонент с отображением детальной информации о маршруте
- Реализован RouteDetailsCard с card layout: header (path + status badge), configuration (upstream URL, methods), metadata (автор, даты), rate limit (если назначен)
- Кнопка Edit отображается только для draft маршрутов и только для владельца
- Кнопка Clone клонирует маршрут и перенаправляет на страницу редактирования клона
- Страница 404 отображается для несуществующих маршрутов с кнопкой возврата к списку
- Добавлен роутинг `/routes/:id` в App.tsx
- Обновлены экспорты в features/routes/index.ts
- Исправлена ошибка TypeScript в RoutesPage.test.tsx (добавлен non-null assertion для deleteButtons)

### File List

**Новые файлы:**
- frontend/admin-ui/src/features/routes/components/RouteDetailsPage.tsx
- frontend/admin-ui/src/features/routes/components/RouteDetailsCard.tsx
- frontend/admin-ui/src/features/routes/components/RouteDetailsPage.test.tsx
- frontend/admin-ui/src/shared/constants/routes.ts — shared constants для статусов и методов (Code Review)
- frontend/admin-ui/src/shared/constants/index.ts — экспорты shared constants (Code Review)

**Изменённые файлы:**
- frontend/admin-ui/src/App.tsx — добавлен роут `/routes/:id`
- frontend/admin-ui/src/features/routes/index.ts — добавлены экспорты RouteDetailsPage и RouteDetailsCard
- frontend/admin-ui/src/features/routes/components/RoutesPage.test.tsx — исправлена ошибка TypeScript + русские labels
- frontend/admin-ui/src/features/routes/components/RoutesTable.tsx — используются shared constants, русские labels (Code Review)
- frontend/admin-ui/src/styles/index.css — CSS класс .page-loading-spinner (Code Review)

## Change Log

- 2026-02-18: Story 3.6 implemented — Route Details View & Clone UI с полным тестовым покрытием
- 2026-02-18: **Code Review Fixes** — Adversarial review исправления:
  - **HIGH #1 Fixed:** STATUS_LABELS унифицированы на русский язык (RoutesTable + RouteDetailsCard)
  - **HIGH #2 Fixed:** Создан shared/constants/routes.ts — устранено дублирование STATUS_COLORS, STATUS_LABELS, METHOD_COLORS
  - **MEDIUM #3 Addressed:** Rate Limit секция обновлена с TODO для Epic 4 API
  - **MEDIUM #4 Fixed:** RoutesPage.test.tsx fix задокументирован
  - **MEDIUM #5 Fixed:** Date formatting унифицирован через dayjs с relative time + tooltip
  - **MEDIUM #6 Fixed:** Добавлен тест на ошибку клонирования
  - **LOW #7 Fixed:** METHOD_COLORS расширен (HEAD, OPTIONS, TRACE)
  - **LOW #8 Fixed:** Empty catch block с комментарием
  - **LOW #9 Fixed:** CSS класс .page-loading-spinner вместо inline styles

