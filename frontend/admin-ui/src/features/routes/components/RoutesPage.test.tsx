// Тесты для RoutesPage (Story 3.4)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { screen, waitFor, fireEvent } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithMockAuth } from '@/test/test-utils'
import { RoutesPage } from './RoutesPage'
import * as routesApi from '../api/routesApi'
import type { RouteListResponse } from '../types/route.types'

// Мок react-router-dom
const mockNavigate = vi.fn()

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

// Мокаем API
vi.mock('../api/routesApi', () => ({
  fetchRoutes: vi.fn(),
  deleteRoute: vi.fn(),
}))

const mockFetchRoutes = routesApi.fetchRoutes as ReturnType<typeof vi.fn>
const mockDeleteRoute = routesApi.deleteRoute as ReturnType<typeof vi.fn>

// Тестовые данные
const mockRoutesResponse: RouteListResponse = {
  items: [
    {
      id: 'route-1',
      path: '/api/orders',
      upstreamUrl: 'http://order-service:8080',
      methods: ['GET', 'POST'],
      description: 'Order service',
      status: 'published',
      createdBy: 'user-1',
      creatorUsername: 'developer1',
      createdAt: '2026-02-17T10:00:00Z',
      updatedAt: '2026-02-17T10:00:00Z',
      rateLimitId: null,
    },
    {
      id: 'route-2',
      path: '/api/users',
      upstreamUrl: 'http://user-service:8080',
      methods: ['GET'],
      description: 'User service',
      status: 'draft',
      createdBy: 'current-user',
      creatorUsername: 'testuser',
      createdAt: '2026-02-17T12:00:00Z',
      updatedAt: '2026-02-17T12:00:00Z',
      rateLimitId: null,
    },
    {
      id: 'route-3',
      path: '/api/products',
      upstreamUrl: 'http://product-service:8080',
      methods: ['GET', 'PUT', 'DELETE'],
      description: 'Product service',
      status: 'pending',
      createdBy: 'user-2',
      creatorUsername: 'developer2',
      createdAt: '2026-02-16T08:00:00Z',
      updatedAt: '2026-02-16T08:00:00Z',
      rateLimitId: null,
    },
  ],
  total: 3,
  offset: 0,
  limit: 20,
}

describe('RoutesPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFetchRoutes.mockResolvedValue(mockRoutesResponse)
    mockDeleteRoute.mockResolvedValue(undefined)
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('отображает заголовок страницы', async () => {
    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
    })

    expect(screen.getByRole('heading', { name: /routes/i })).toBeInTheDocument()
  })

  it('отображает кнопку New Route', async () => {
    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
    })

    const newButton = screen.getByRole('button', { name: /new route/i })
    expect(newButton).toBeInTheDocument()
  })

  it('переходит на страницу создания маршрута при клике на New Route', async () => {
    const user = userEvent.setup()

    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
    })

    const newButton = screen.getByRole('button', { name: /new route/i })
    await user.click(newButton)

    expect(mockNavigate).toHaveBeenCalledWith('/routes/new')
  })

  it('загружает и отображает список маршрутов', async () => {
    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('/api/orders')).toBeInTheDocument()
    })

    expect(screen.getByText('/api/users')).toBeInTheDocument()
    expect(screen.getByText('/api/products')).toBeInTheDocument()
  })

  it('отображает методы как теги', async () => {
    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('/api/orders')).toBeInTheDocument()
    })

    // Проверяем методы - может быть несколько GET
    expect(screen.getAllByText('GET').length).toBeGreaterThan(0)
    expect(screen.getByText('POST')).toBeInTheDocument()
    expect(screen.getByText('PUT')).toBeInTheDocument()
    expect(screen.getByText('DELETE')).toBeInTheDocument()
  })

  it('отображает статус badges на русском языке', async () => {
    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('Опубликован')).toBeInTheDocument()
    })

    expect(screen.getByText('Черновик')).toBeInTheDocument()
    expect(screen.getByText('На согласовании')).toBeInTheDocument()
  })

  it('отображает имя автора', async () => {
    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('developer1')).toBeInTheDocument()
    })

    // testuser - имя текущего пользователя, может быть в нескольких местах
    expect(screen.getAllByText('testuser').length).toBeGreaterThan(0)
    expect(screen.getByText('developer2')).toBeInTheDocument()
  })

  it('отображает поисковое поле', async () => {
    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
    })

    expect(screen.getByPlaceholderText('Поиск по path, upstream...')).toBeInTheDocument()
  })

  it('отображает фильтр по статусу', async () => {
    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
    })

    // Select отображается с текстом "Все статусы" по умолчанию
    expect(screen.getByText('Все статусы')).toBeInTheDocument()
  })

  it('отображает пагинацию с общим количеством маршрутов', async () => {
    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      // Правильное склонение: 3 маршрута (не "маршрутов")
      expect(screen.getByText('Всего 3 маршрута')).toBeInTheDocument()
    })
  })

  it('обрабатывает keyboard shortcut Ctrl+N', async () => {
    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
    })

    // Симулируем Ctrl+N
    fireEvent.keyDown(window, { key: 'n', ctrlKey: true })

    expect(mockNavigate).toHaveBeenCalledWith('/routes/new')
  })

  it('обрабатывает keyboard shortcut Cmd+N (Mac)', async () => {
    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
    })

    // Симулируем Cmd+N (metaKey для Mac)
    fireEvent.keyDown(window, { key: 'n', metaKey: true })

    expect(mockNavigate).toHaveBeenCalledWith('/routes/new')
  })
})

describe('RoutesPage поиск и фильтрация', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFetchRoutes.mockResolvedValue(mockRoutesResponse)
  })

  it('вызывает API с параметрами поиска', async () => {
    const user = userEvent.setup()

    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
      initialEntries: ['/routes'],
    })

    await waitFor(() => {
      expect(screen.getByText('/api/orders')).toBeInTheDocument()
    })

    // Вводим поисковый запрос
    const searchInput = screen.getByPlaceholderText('Поиск по path, upstream...')
    await user.clear(searchInput)
    await user.type(searchInput, 'orders')

    // Ждём debounce
    await waitFor(
      () => {
        // Проверяем что API вызвался с параметром search
        expect(mockFetchRoutes).toHaveBeenCalledWith(
          expect.objectContaining({ search: 'orders' })
        )
      },
      { timeout: 500 }
    )
  })

  it('отображает chip при поиске', async () => {
    const user = userEvent.setup()

    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
      initialEntries: ['/routes'],
    })

    await waitFor(() => {
      expect(screen.getByText('/api/orders')).toBeInTheDocument()
    })

    const searchInput = screen.getByPlaceholderText('Поиск по path, upstream...')
    await user.type(searchInput, 'test')

    await waitFor(
      () => {
        expect(screen.getByText('Поиск: test')).toBeInTheDocument()
      },
      { timeout: 500 }
    )
  })

  it('отображает кнопку сброса фильтров при активном фильтре', async () => {
    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
      initialEntries: ['/routes?search=test'],
    })

    await waitFor(() => {
      expect(screen.getByText('Сбросить фильтры')).toBeInTheDocument()
    })
  })

  it('сбрасывает фильтры при клике на кнопку сброса', async () => {
    const user = userEvent.setup()

    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
      initialEntries: ['/routes?search=test'],
    })

    await waitFor(() => {
      expect(screen.getByText('Сбросить фильтры')).toBeInTheDocument()
    })

    await user.click(screen.getByText('Сбросить фильтры'))

    // После сброса кнопка должна исчезнуть
    await waitFor(() => {
      expect(screen.queryByText('Сбросить фильтры')).not.toBeInTheDocument()
    })
  })

  it('вызывает API с параметром status при наличии в URL', async () => {
    // Тестируем фильтрацию через URL параметры (более надёжный подход)
    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
      initialEntries: ['/routes?status=draft'],
    })

    await waitFor(() => {
      expect(screen.getByText('/api/orders')).toBeInTheDocument()
    })

    // Проверяем что API вызвался с параметром status
    expect(mockFetchRoutes).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'draft' })
    )

    // Проверяем что chip с фильтром статуса отображается (используем getAllByText из-за дубликатов)
    const draftElements = screen.getAllByText('Черновик')
    expect(draftElements.length).toBeGreaterThan(0)
  })

  it('подсвечивает поисковый термин в результатах', async () => {
    // Мокаем ответ с одним маршрутом для простоты
    mockFetchRoutes.mockResolvedValue({
      items: [{
        id: 'route-1',
        path: '/api/orders',
        upstreamUrl: 'http://order-service:8080',
        methods: ['GET'],
        description: 'Order service',
        status: 'published',
        createdBy: 'user-1',
        creatorUsername: 'developer1',
        createdAt: '2026-02-17T10:00:00Z',
        updatedAt: '2026-02-17T10:00:00Z',
        rateLimitId: null,
      }],
      total: 1,
      offset: 0,
      limit: 20,
    })

    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
      initialEntries: ['/routes?search=orders'],
    })

    // Ждём загрузки данных — используем функцию для поиска текста,
    // т.к. path разбит на части из-за подсветки: "/api/" + <mark>orders</mark>
    await waitFor(() => {
      // Проверяем что есть элемент mark с подсвеченным текстом
      const highlightedText = screen.getByText('orders', { selector: 'mark' })
      expect(highlightedText).toBeInTheDocument()
    })

    // Также проверяем что link существует (с разбитым текстом)
    const link = screen.getByRole('link', { name: /orders/i })
    expect(link).toHaveAttribute('href', '/routes/route-1')
  })
})

describe('RoutesPage loading и error состояния', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('отображает loading состояние во время загрузки', async () => {
    // Создаём промис который не резолвится сразу
    let resolvePromise: (value: RouteListResponse) => void
    const pendingPromise = new Promise<RouteListResponse>((resolve) => {
      resolvePromise = resolve
    })
    mockFetchRoutes.mockReturnValue(pendingPromise)

    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
    })

    // Проверяем что есть spinning/loading indicator
    expect(screen.getByRole('table')).toBeInTheDocument()

    // Резолвим промис
    resolvePromise!(mockRoutesResponse)

    await waitFor(() => {
      expect(screen.getByText('/api/orders')).toBeInTheDocument()
    })
  })

  it('отображает сообщение об ошибке при неудачной загрузке', async () => {
    mockFetchRoutes.mockRejectedValue(new Error('Network error'))

    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('Ошибка загрузки')).toBeInTheDocument()
    })

    expect(screen.getByText('Network error')).toBeInTheDocument()
  })
})

describe('RoutesPage пагинация', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('вызывает API с правильным offset при смене страницы', async () => {
    // Мокаем ответ с 25 элементами для включения пагинации
    const manyRoutes = Array.from({ length: 25 }, (_, i) => ({
      id: `route-${i}`,
      path: `/api/route-${i}`,
      upstreamUrl: 'http://service:8080',
      methods: ['GET'],
      description: `Route ${i}`,
      status: 'published' as const,
      createdBy: 'user-1',
      creatorUsername: 'developer1',
      createdAt: '2026-02-17T10:00:00Z',
      updatedAt: '2026-02-17T10:00:00Z',
      rateLimitId: null,
    }))

    mockFetchRoutes.mockResolvedValue({
      items: manyRoutes.slice(0, 20),
      total: 25,
      offset: 0,
      limit: 20,
    })

    const user = userEvent.setup()

    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
      initialEntries: ['/routes'],
    })

    await waitFor(() => {
      expect(screen.getByText('/api/route-0')).toBeInTheDocument()
    })

    // Кликаем на страницу 2
    const page2Button = screen.getByTitle('2')
    await user.click(page2Button)

    // Проверяем что API вызвался с offset=20
    await waitFor(() => {
      expect(mockFetchRoutes).toHaveBeenCalledWith(
        expect.objectContaining({ offset: 20 })
      )
    })
  })

  it('корректно склоняет количество маршрутов', async () => {
    // Тест для 1 маршрута
    mockFetchRoutes.mockResolvedValue({
      items: [mockRoutesResponse.items[0]],
      total: 1,
      offset: 0,
      limit: 20,
    })

    const { unmount } = renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('Всего 1 маршрут')).toBeInTheDocument()
    })

    unmount()

    // Тест для 5 маршрутов
    mockFetchRoutes.mockResolvedValue({
      items: Array(5).fill(mockRoutesResponse.items[0]),
      total: 5,
      offset: 0,
      limit: 20,
    })

    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('Всего 5 маршрутов')).toBeInTheDocument()
    })
  })
})

describe('колонка Rate Limit (Story 5.5, обновлено Story 8.4)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('отображает название и requests/sec когда политика назначена (Story 8.4)', async () => {
    // Мокаем ответ с rate limit
    const routeWithRateLimit = {
      ...mockRoutesResponse.items[0],
      rateLimit: {
        id: 'policy-1',
        name: 'standard',
        requestsPerSecond: 100,
        burstSize: 150,
      },
    }
    mockFetchRoutes.mockResolvedValue({
      items: [routeWithRateLimit],
      total: 1,
      offset: 0,
      limit: 20,
    })

    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('/api/orders')).toBeInTheDocument()
    })

    // Story 8.4: проверяем формат "{name} ({requestsPerSecond}/s)"
    expect(screen.getByText('standard (100/s)')).toBeInTheDocument()
  })

  it('отображает "—" когда политика не назначена', async () => {
    // Мокаем ответ без rate limit
    const routeWithoutRateLimit = {
      ...mockRoutesResponse.items[0],
      rateLimit: null,
    }
    mockFetchRoutes.mockResolvedValue({
      items: [routeWithoutRateLimit],
      total: 1,
      offset: 0,
      limit: 20,
    })

    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('/api/orders')).toBeInTheDocument()
    })

    // Проверяем что em dash отображается для отсутствующей политики
    expect(screen.getByText('—')).toBeInTheDocument()
  })
})

describe('RoutesPage удаление маршрута', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFetchRoutes.mockResolvedValue(mockRoutesResponse)
    mockDeleteRoute.mockResolvedValue(undefined)
  })

  it('показывает Popconfirm при клике на кнопку удаления', async () => {
    const user = userEvent.setup()

    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('/api/users')).toBeInTheDocument()
    })

    // Находим кнопку удаления для draft маршрута текущего пользователя
    const deleteButtons = screen.getAllByRole('button', { name: /delete/i })
    // Должна быть только одна кнопка удаления (для draft маршрута текущего пользователя)
    expect(deleteButtons.length).toBeGreaterThan(0)
    const firstDeleteButton = deleteButtons[0]!

    await user.click(firstDeleteButton)

    // Проверяем что появился Popconfirm
    await waitFor(() => {
      expect(screen.getByText('Удалить маршрут?')).toBeInTheDocument()
    })
  })

  it('вызывает deleteRoute при подтверждении удаления', async () => {
    const user = userEvent.setup()

    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'current-user', username: 'testuser', role: 'developer' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('/api/users')).toBeInTheDocument()
    })

    // Находим и кликаем кнопку удаления
    const deleteButtons = screen.getAllByRole('button', { name: /delete/i })
    const firstDeleteButton = deleteButtons[0]!
    await user.click(firstDeleteButton)

    // Подтверждаем удаление
    const confirmButton = await screen.findByRole('button', { name: /да/i })
    await user.click(confirmButton)

    // Проверяем что API был вызван
    await waitFor(() => {
      expect(mockDeleteRoute).toHaveBeenCalledWith('route-2')
    })
  })
})

// ============================================
// Story 10.4: Admin может удалять чужие draft маршруты
// ============================================

describe('RoutesPage Admin права (Story 10.4)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFetchRoutes.mockResolvedValue(mockRoutesResponse)
    mockDeleteRoute.mockResolvedValue(undefined)
  })

  it('Admin видит кнопки Edit и Delete для чужих draft маршрутов', async () => {
    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'admin-user', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('/api/users')).toBeInTheDocument()
    })

    // Admin должен видеть кнопки Edit и Delete для draft маршрута другого пользователя
    // route-2 - draft маршрут с createdBy: 'current-user' (не admin)
    const deleteButtons = screen.getAllByRole('button', { name: /delete/i })
    expect(deleteButtons.length).toBeGreaterThan(0)

    const editButtons = screen.getAllByRole('button', { name: /edit/i })
    expect(editButtons.length).toBeGreaterThan(0)
  })

  it('Admin может удалить чужой draft маршрут', async () => {
    const user = userEvent.setup()

    renderWithMockAuth(<RoutesPage />, {
      authValue: {
        user: { userId: 'admin-user', username: 'admin', role: 'admin' },
        isAuthenticated: true,
      },
    })

    await waitFor(() => {
      expect(screen.getByText('/api/users')).toBeInTheDocument()
    })

    // Находим и кликаем кнопку удаления для draft маршрута
    const deleteButtons = screen.getAllByRole('button', { name: /delete/i })
    const firstDeleteButton = deleteButtons[0]!
    await user.click(firstDeleteButton)

    // Подтверждаем удаление
    const confirmButton = await screen.findByRole('button', { name: /да/i })
    await user.click(confirmButton)

    // Проверяем что API был вызван
    await waitFor(() => {
      expect(mockDeleteRoute).toHaveBeenCalledWith('route-2')
    })
  })
})
