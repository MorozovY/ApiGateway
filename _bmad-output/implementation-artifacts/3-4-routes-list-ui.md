# Story 3.4: Routes List UI

Status: done

<!-- Note: Validation is optional. Run validate-create-story for quality check before dev-story. -->

## Story

As a **Developer**,
I want a routes list page with filtering and search,
So that I can efficiently manage all routes.

## Acceptance Criteria

1. **AC1: Отображение таблицы маршрутов**
   **Given** пользователь переходит на `/routes`
   **When** страница загружается
   **Then** ProTable отображает маршруты с колонками:
   - Path (кликабельный, ведёт к деталям)
   - Upstream URL
   - Methods (теги: GET, POST, и т.д.)
   - Status (badge: Draft серый, Pending жёлтый, Published зелёный, Rejected красный)
   - Author (username)
   - Created (относительное время, например "2 hours ago")
   - Actions (Edit, Delete для drafts; View для остальных)

2. **AC2: Поиск в реальном времени**
   **Given** список маршрутов отображается
   **When** пользователь вводит текст в поисковое поле
   **Then** список фильтруется в реальном времени (debounced 300ms)
   **And** поисковый термин подсвечивается в результатах

3. **AC3: Фильтрация по статусу**
   **Given** список маршрутов отображается
   **When** пользователь выбирает фильтр статуса в dropdown
   **Then** список обновляется, показывая только совпадающие маршруты
   **And** активный фильтр показывается как chip, который можно удалить

4. **AC4: Кнопка создания нового маршрута**
   **Given** пользователь нажимает кнопку "New Route"
   **When** действие срабатывает
   **Then** пользователь переходит на форму создания маршрута
   **And** keyboard shortcut ⌘+N (Ctrl+N) также вызывает это действие

5. **AC5: Пагинация**
   **Given** элементы управления пагинацией
   **When** пользователь меняет страницу или размер страницы
   **Then** список обновляется без полной перезагрузки страницы
   **And** URL query params обновляются для возможности bookmarking

## Tasks / Subtasks

- [x] **Task 1: Создать типы и API клиент для routes** (AC: #1)
  - [x] Subtask 1.1: Создать `features/routes/types/route.types.ts` с типами Route, RouteStatus, RouteListParams, RouteListResponse
  - [x] Subtask 1.2: Создать `features/routes/api/routesApi.ts` с методами getRoutes(), getRoute(), createRoute(), updateRoute(), deleteRoute()

- [x] **Task 2: Создать React Query hooks** (AC: #1, #2, #3, #5)
  - [x] Subtask 2.1: Создать `features/routes/hooks/useRoutes.ts` с хуками useRoutes, useRoute, useCreateRoute, useUpdateRoute, useDeleteRoute
  - [x] Subtask 2.2: Реализовать кэширование и инвалидацию React Query

- [x] **Task 3: Создать компонент RoutesTable** (AC: #1, #2, #3, #5)
  - [x] Subtask 3.1: Создать `features/routes/components/RoutesTable.tsx` на базе Ant Design Table/ProTable
  - [x] Subtask 3.2: Реализовать колонки: Path, Upstream URL, Methods, Status, Author, Created, Actions
  - [x] Subtask 3.3: Добавить Status Badge с цветовым кодированием (Draft серый, Pending жёлтый, Published зелёный, Rejected красный)
  - [x] Subtask 3.4: Добавить Methods как Tags
  - [x] Subtask 3.5: Добавить Created как relative time (используя date-fns или dayjs)
  - [x] Subtask 3.6: Реализовать Actions (Edit/Delete для draft, View для остальных)
  - [x] Subtask 3.7: Добавить пагинацию с обновлением URL query params

- [x] **Task 4: Реализовать поиск и фильтрацию** (AC: #2, #3)
  - [x] Subtask 4.1: Добавить поисковое поле с debounce (300ms)
  - [x] Subtask 4.2: Добавить фильтр по статусу (Select dropdown)
  - [x] Subtask 4.3: Отображать активные фильтры как chips/tags
  - [x] Subtask 4.4: Синхронизировать фильтры с URL query params

- [x] **Task 5: Обновить RoutesPage** (AC: #1, #4)
  - [x] Subtask 5.1: Заменить placeholder в `features/routes/components/RoutesPage.tsx`
  - [x] Subtask 5.2: Добавить PageHeader с заголовком и кнопкой "New Route"
  - [x] Subtask 5.3: Добавить keyboard shortcut ⌘+N (Ctrl+N) для создания маршрута
  - [x] Subtask 5.4: Интегрировать RoutesTable

- [x] **Task 6: Создать тесты** (AC: #1, #2, #3, #4, #5)
  - [x] Subtask 6.1: Создать `RoutesPage.test.tsx` с тестами рендеринга
  - [x] Subtask 6.2: Тест загрузки списка маршрутов
  - [x] Subtask 6.3: Тест поиска с debounce
  - [x] Subtask 6.4: Тест фильтрации по статусу
  - [x] Subtask 6.5: Тест пагинации
  - [x] Subtask 6.6: Тест keyboard shortcut ⌘+N

## Dev Notes

### Previous Story Intelligence (Story 3.3 — Route Details & Clone API)

**Реализовано в Backend (Story 3.1-3.3):**
- API endpoints готовы:
  - `GET /api/v1/routes` — список с фильтрацией и пагинацией
  - `GET /api/v1/routes/{id}` — детали маршрута с creatorUsername
  - `POST /api/v1/routes` — создание маршрута
  - `PUT /api/v1/routes/{id}` — обновление маршрута
  - `DELETE /api/v1/routes/{id}` — удаление маршрута
  - `POST /api/v1/routes/{id}/clone` — клонирование маршрута

**API Response Formats (из Story 3.2):**

Список маршрутов:
```json
{
  "items": [
    {
      "id": "uuid",
      "path": "/api/orders",
      "upstreamUrl": "http://order-service:8080",
      "methods": ["GET", "POST"],
      "description": "Order service endpoints",
      "status": "published",
      "createdBy": "user-uuid",
      "createdAt": "2026-02-11T10:30:00Z",
      "updatedAt": "2026-02-11T10:35:00Z"
    }
  ],
  "total": 156,
  "offset": 0,
  "limit": 20
}
```

**Query Parameters (из Story 3.2):**
- `status` — фильтр по статусу (draft, pending, published, rejected)
- `createdBy` — фильтр по автору ("me" для текущего пользователя)
- `search` — поиск по path и description (case-insensitive)
- `offset` — смещение для пагинации
- `limit` — количество элементов на странице

---

### Architecture Compliance

**Из architecture.md — Frontend Architecture:**

| Решение | Выбор | Применение |
|---------|-------|------------|
| **Build Tool** | Vite | Уже настроен |
| **State Management** | React Query + Context | useRoutes hook с React Query |
| **UI Library** | Ant Design | Table, Select, Input, Tag, Button, Badge |
| **Forms** | React Hook Form + Zod | Для RouteFormModal (Story 3.5) |
| **Routing** | React Router v6 | useNavigate, useSearchParams |
| **HTTP Client** | Axios | Уже настроен в shared/utils/axios.ts |

**Из ux-design-specification.md:**

| Паттерн | Источник | Применение |
|---------|----------|------------|
| **ProTable** | Ant Design Pro | Таблица с фильтрами и actions |
| **Status Badges** | Vercel | Draft/Pending/Published/Rejected статусы |
| **Inline Actions** | GitHub | Edit/Delete/View без перехода |
| **Visible Filters** | Best Practice | Filter bar с chips |
| **Keyboard Shortcuts** | Power Users | ⌘+N для создания |

**Status Badge Colors:**
- Draft: `gray` / `default`
- Pending: `gold` / `processing`
- Published: `green` / `success`
- Rejected: `red` / `error`

---

### Technical Requirements

**1. Типы для Routes:**

```typescript
// features/routes/types/route.types.ts

export type RouteStatus = 'draft' | 'pending' | 'published' | 'rejected';

export interface Route {
  id: string;
  path: string;
  upstreamUrl: string;
  methods: string[];
  description: string | null;
  status: RouteStatus;
  createdBy: string;
  creatorUsername?: string;
  createdAt: string;
  updatedAt: string;
  rateLimitId: string | null;
}

export interface RouteListParams {
  offset?: number;
  limit?: number;
  status?: RouteStatus;
  search?: string;
  createdBy?: string;
}

export interface RouteListResponse {
  items: Route[];
  total: number;
  offset: number;
  limit: number;
}

export interface CreateRouteRequest {
  path: string;
  upstreamUrl: string;
  methods: string[];
  description?: string;
}

export interface UpdateRouteRequest {
  path?: string;
  upstreamUrl?: string;
  methods?: string[];
  description?: string;
}
```

**2. API Client (по паттерну usersApi.ts):**

```typescript
// features/routes/api/routesApi.ts
import axios from '../../../shared/utils/axios';
import type { Route, RouteListParams, RouteListResponse, CreateRouteRequest, UpdateRouteRequest } from '../types/route.types';

const BASE_URL = '/api/v1/routes';

export const routesApi = {
  getRoutes: async (params: RouteListParams = {}): Promise<RouteListResponse> => {
    const { data } = await axios.get<RouteListResponse>(BASE_URL, { params });
    return data;
  },

  getRoute: async (id: string): Promise<Route> => {
    const { data } = await axios.get<Route>(`${BASE_URL}/${id}`);
    return data;
  },

  createRoute: async (request: CreateRouteRequest): Promise<Route> => {
    const { data } = await axios.post<Route>(BASE_URL, request);
    return data;
  },

  updateRoute: async (id: string, request: UpdateRouteRequest): Promise<Route> => {
    const { data } = await axios.put<Route>(`${BASE_URL}/${id}`, request);
    return data;
  },

  deleteRoute: async (id: string): Promise<void> => {
    await axios.delete(`${BASE_URL}/${id}`);
  },

  cloneRoute: async (id: string): Promise<Route> => {
    const { data } = await axios.post<Route>(`${BASE_URL}/${id}/clone`);
    return data;
  },
};
```

**3. React Query Hooks (по паттерну useUsers.ts):**

```typescript
// features/routes/hooks/useRoutes.ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { routesApi } from '../api/routesApi';
import type { RouteListParams, CreateRouteRequest, UpdateRouteRequest } from '../types/route.types';

export const ROUTES_QUERY_KEY = 'routes';

export function useRoutes(params: RouteListParams = {}) {
  return useQuery({
    queryKey: [ROUTES_QUERY_KEY, params],
    queryFn: () => routesApi.getRoutes(params),
  });
}

export function useRoute(id: string) {
  return useQuery({
    queryKey: [ROUTES_QUERY_KEY, id],
    queryFn: () => routesApi.getRoute(id),
    enabled: !!id,
  });
}

export function useCreateRoute() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: CreateRouteRequest) => routesApi.createRoute(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [ROUTES_QUERY_KEY] });
    },
  });
}

export function useUpdateRoute() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, request }: { id: string; request: UpdateRouteRequest }) =>
      routesApi.updateRoute(id, request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [ROUTES_QUERY_KEY] });
    },
  });
}

export function useDeleteRoute() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => routesApi.deleteRoute(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [ROUTES_QUERY_KEY] });
    },
  });
}

export function useCloneRoute() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => routesApi.cloneRoute(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: [ROUTES_QUERY_KEY] });
    },
  });
}
```

**4. Status Badge Component:**

```typescript
// Использовать Ant Design Tag с preset цветами
import { Tag } from 'antd';

const STATUS_COLORS: Record<RouteStatus, string> = {
  draft: 'default',
  pending: 'processing',
  published: 'success',
  rejected: 'error',
};

const STATUS_LABELS: Record<RouteStatus, string> = {
  draft: 'Draft',
  pending: 'Pending',
  published: 'Published',
  rejected: 'Rejected',
};

// В колонке таблицы:
<Tag color={STATUS_COLORS[status]}>{STATUS_LABELS[status]}</Tag>
```

**5. Methods Tags:**

```typescript
// Использовать Ant Design Tag
import { Tag } from 'antd';

const METHOD_COLORS: Record<string, string> = {
  GET: 'green',
  POST: 'blue',
  PUT: 'orange',
  DELETE: 'red',
  PATCH: 'purple',
};

// В колонке таблицы:
{methods.map(method => (
  <Tag key={method} color={METHOD_COLORS[method] || 'default'}>{method}</Tag>
))}
```

**6. Relative Time (использовать dayjs):**

```typescript
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';

dayjs.extend(relativeTime);

// В колонке Created:
dayjs(createdAt).fromNow()  // "2 hours ago"
```

**7. URL Query Params Sync:**

```typescript
import { useSearchParams } from 'react-router-dom';

// В RoutesPage:
const [searchParams, setSearchParams] = useSearchParams();

const params: RouteListParams = {
  offset: Number(searchParams.get('offset')) || 0,
  limit: Number(searchParams.get('limit')) || 20,
  status: searchParams.get('status') as RouteStatus | undefined,
  search: searchParams.get('search') || undefined,
};

// При изменении фильтров:
setSearchParams({
  ...Object.fromEntries(searchParams),
  offset: '0', // Сбросить на первую страницу
  status: newStatus,
});
```

**8. Keyboard Shortcut (⌘+N / Ctrl+N):**

```typescript
import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

// В RoutesPage:
const navigate = useNavigate();

useEffect(() => {
  const handleKeyDown = (e: KeyboardEvent) => {
    if ((e.metaKey || e.ctrlKey) && e.key === 'n') {
      e.preventDefault();
      navigate('/routes/new');
    }
  };

  window.addEventListener('keydown', handleKeyDown);
  return () => window.removeEventListener('keydown', handleKeyDown);
}, [navigate]);
```

---

### Project Structure Notes

**Файлы для создания:**
```
frontend/admin-ui/src/features/routes/
├── api/
│   └── routesApi.ts                    (NEW)
├── components/
│   ├── RoutesPage.tsx                  (MODIFY — заменить placeholder)
│   ├── RoutesPage.test.tsx             (NEW)
│   ├── RoutesTable.tsx                 (NEW)
│   └── RouteStatusBadge.tsx            (NEW — опционально, для reusability)
├── hooks/
│   └── useRoutes.ts                    (NEW)
├── types/
│   └── route.types.ts                  (NEW)
└── index.ts                            (MODIFY — добавить экспорты)
```

**Зависимости (добавить в package.json если отсутствуют):**
- `dayjs` — для relative time (или использовать date-fns)
- `use-debounce` — для debounce поиска (или реализовать вручную)

---

### Testing Standards

**Из CLAUDE.md:**
- Названия тестов ОБЯЗАТЕЛЬНО на русском языке
- Комментарии в коде на русском языке
- Использовать Vitest (уже настроен)
- Использовать test-utils.tsx для рендеринга с провайдерами

**Примеры тестов:**

```typescript
// features/routes/components/RoutesPage.test.tsx
import { describe, it, expect, vi } from 'vitest';
import { render, screen, waitFor } from '../../../test/test-utils';
import { RoutesPage } from './RoutesPage';

// Мок API
vi.mock('../api/routesApi', () => ({
  routesApi: {
    getRoutes: vi.fn().mockResolvedValue({
      items: [
        {
          id: '1',
          path: '/api/orders',
          upstreamUrl: 'http://order-service:8080',
          methods: ['GET', 'POST'],
          status: 'published',
          createdBy: 'user-1',
          createdAt: '2026-02-17T10:00:00Z',
          updatedAt: '2026-02-17T10:00:00Z',
        },
      ],
      total: 1,
      offset: 0,
      limit: 20,
    }),
  },
}));

describe('RoutesPage', () => {
  it('отображает заголовок страницы', async () => {
    render(<RoutesPage />);
    expect(screen.getByText('Routes')).toBeInTheDocument();
  });

  it('загружает и отображает список маршрутов', async () => {
    render(<RoutesPage />);

    await waitFor(() => {
      expect(screen.getByText('/api/orders')).toBeInTheDocument();
    });
  });

  it('отображает кнопку New Route', () => {
    render(<RoutesPage />);
    expect(screen.getByRole('button', { name: /New Route/i })).toBeInTheDocument();
  });

  it('отображает статус badge с правильным цветом', async () => {
    render(<RoutesPage />);

    await waitFor(() => {
      const badge = screen.getByText('Published');
      expect(badge).toHaveClass('ant-tag-success');
    });
  });

  it('отображает методы как теги', async () => {
    render(<RoutesPage />);

    await waitFor(() => {
      expect(screen.getByText('GET')).toBeInTheDocument();
      expect(screen.getByText('POST')).toBeInTheDocument();
    });
  });
});
```

---

### Git Intelligence

**Последние коммиты (Epic 3):**
```
a42d6ae feat: Route List Filtering, Details & Clone API (Stories 3.2, 3.3)
9fcd455 feat: Route CRUD API with ownership and status validation (Story 3.1)
```

**Релевантные файлы из предыдущих историй:**
- `features/users/` — образец структуры feature модуля
- `features/users/api/usersApi.ts` — паттерн API клиента
- `features/users/hooks/useUsers.ts` — паттерн React Query hooks
- `features/users/components/UsersTable.tsx` — паттерн таблицы с пагинацией
- `shared/utils/axios.ts` — настроенный HTTP клиент с RFC 7807

**Commit message format:**
```
feat: Routes List UI with filtering and search (Story 3.4)

- Add RoutesTable component with pagination and filtering
- Add status badges with color coding
- Add real-time search with debounce
- Add keyboard shortcut Ctrl+N for new route
- Sync filters with URL query params

Co-Authored-By: Claude Opus 4.5 <noreply@anthropic.com>
```

---

### References

- [Source: architecture.md#Frontend Architecture] — React Query, Ant Design, Axios
- [Source: ux-design-specification.md#UX Pattern Analysis] — ProTable, Status Badges, Inline Actions
- [Source: epics.md#Story 3.4] — Acceptance Criteria (FR4)
- [Source: CLAUDE.md] — Русские комментарии и названия тестов
- [Source: 3-3-route-details-clone-api.md] — Backend API response formats
- [Source: features/users/] — Паттерны структуры feature модуля

---

## Dev Agent Record

### Agent Model Used

Claude Opus 4.5 (claude-opus-4-5-20251101)

### Debug Log References

- TypeScript компиляция прошла успешно
- Все 68 тестов проходят (24 для Routes + 44 существующих)
- Линтер warnings не относятся к изменённым файлам

### Completion Notes List

- Реализована полнофункциональная страница Routes List с таблицей, фильтрацией и поиском
- Типы соответствуют API формату из Stories 3.1-3.3
- React Query hooks с полным CRUD и инвалидацией кэша
- Поиск с debounce (300ms), фильтр по статусу, chips для активных фильтров
- URL query params синхронизация для bookmarking
- Keyboard shortcut Ctrl+N / Cmd+N для создания маршрута
- Status badges с цветовым кодированием (Draft серый, Pending жёлтый, Published зелёный, Rejected красный)
- Methods отображаются как цветные Tags (GET зелёный, POST синий, PUT оранжевый, DELETE красный)
- Relative time для Created через dayjs
- Actions: Edit/Delete для draft маршрутов текущего пользователя, View для остальных
- **[Code Review Fix]** Подсветка поискового термина в результатах (AC2)
- **[Code Review Fix]** Правильное склонение числительных в пагинации (1 маршрут, 2 маршрута, 5 маршрутов)
- **[Code Review Fix]** Отображение ошибок загрузки через Alert
- 24 теста покрывают все Acceptance Criteria

### File List

**Новые файлы:**
- frontend/admin-ui/src/features/routes/types/route.types.ts
- frontend/admin-ui/src/features/routes/api/routesApi.ts
- frontend/admin-ui/src/features/routes/hooks/useRoutes.ts
- frontend/admin-ui/src/features/routes/components/RoutesTable.tsx
- frontend/admin-ui/src/features/routes/components/RoutesPage.test.tsx

**Изменённые файлы:**
- frontend/admin-ui/src/features/routes/components/RoutesPage.tsx
- frontend/admin-ui/src/features/routes/index.ts
- frontend/admin-ui/package.json (добавлен dayjs)

### Change Log

**2026-02-17:** Реализация Story 3.4 - Routes List UI
- Добавлены типы Route, RouteStatus, RouteListParams, RouteListResponse
- Добавлен API клиент routesApi с CRUD операциями
- Добавлены React Query hooks useRoutes, useRoute, useCreateRoute, useUpdateRoute, useDeleteRoute, useCloneRoute
- Создан компонент RoutesTable с пагинацией, фильтрацией и поиском
- Обновлён RoutesPage с интеграцией RoutesTable и keyboard shortcut
- Добавлены 16 тестов для RoutesPage

**2026-02-17:** Code Review Fixes (Senior Developer Review)
- Добавлена подсветка поискового термина в колонке Path (AC2 — highlightSearchTerm)
- Исправлено склонение числительных в пагинации (pluralizeRoutes)
- Добавлено отображение ошибок загрузки через Alert компонент
- Добавлены 8 новых тестов: loading state, error state, пагинация, удаление, подсветка поиска, фильтр статуса

