// Тесты для страницы деталей маршрута (Story 3.6)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { screen, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { renderWithMockAuth } from '../../../test/test-utils'
import { RouteDetailsPage } from './RouteDetailsPage'
import type { Route } from '../types/route.types'

// Мок navigate функция
const mockNavigate = vi.fn()

// Мок useParams — будем устанавливать значение перед каждым тестом
let mockParamsValue: Record<string, string> = {}

// Мок react-router-dom
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    useParams: () => mockParamsValue,
  }
})

// Мок данные для тестов
const mockRoute: Route = {
  id: 'route-1',
  path: '/api/orders',
  upstreamUrl: 'http://order-service:8080',
  methods: ['GET', 'POST'],
  description: 'Order service',
  status: 'draft',
  createdBy: 'user-1',
  creatorUsername: 'testuser',
  createdAt: '2026-02-18T10:00:00Z',
  updatedAt: '2026-02-18T10:00:00Z',
  rateLimitId: null,
}

// Мок для React Query hooks — будем устанавливать значение перед каждым тестом
let mockRouteData: Route | undefined = undefined
let mockIsLoadingRoute = false
let mockRouteError: Error | null = null
let mockCloneMutateAsync = vi.fn()
let mockCloneIsPending = false

vi.mock('../hooks/useRoutes', () => ({
  useRoute: () => ({
    data: mockRouteData,
    isLoading: mockIsLoadingRoute,
    error: mockRouteError,
  }),
  useCloneRoute: () => ({
    mutateAsync: mockCloneMutateAsync,
    isPending: mockCloneIsPending,
    reset: vi.fn(),
  }),
  useSubmitRoute: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
  }),
}))

// Мок useAuth
let mockUser = { userId: 'user-1', username: 'testuser', role: 'developer' as const }

vi.mock('@features/auth', async () => {
  const actual = await vi.importActual('@features/auth')
  return {
    ...actual,
    useAuth: () => ({
      user: mockUser,
      isAuthenticated: true,
      isLoading: false,
      error: null,
    }),
  }
})

describe('RouteDetailsPage', () => {
  beforeEach(() => {
    // Сбрасываем все моки перед каждым тестом
    vi.clearAllMocks()
    mockParamsValue = { id: 'route-1' }
    mockRouteData = { ...mockRoute }
    mockIsLoadingRoute = false
    mockRouteError = null
    mockCloneMutateAsync = vi.fn()
    mockCloneIsPending = false
    mockUser = { userId: 'user-1', username: 'testuser', role: 'developer' }
  })

  afterEach(() => {
    cleanup()
  })

  describe('отображение деталей маршрута (AC: #1)', () => {
    it('отображает path маршрута как заголовок', async () => {
      renderWithMockAuth(<RouteDetailsPage />, {
        authValue: { isAuthenticated: true, user: mockUser },
        initialEntries: ['/routes/route-1'],
      })

      await waitFor(() => {
        expect(screen.getByText('/api/orders')).toBeInTheDocument()
      })
    })

    it('отображает status badge', async () => {
      renderWithMockAuth(<RouteDetailsPage />, {
        authValue: { isAuthenticated: true, user: mockUser },
        initialEntries: ['/routes/route-1'],
      })

      await waitFor(() => {
        expect(screen.getByText('Черновик')).toBeInTheDocument()
      })
    })

    it('отображает upstream URL', async () => {
      renderWithMockAuth(<RouteDetailsPage />, {
        authValue: { isAuthenticated: true, user: mockUser },
        initialEntries: ['/routes/route-1'],
      })

      await waitFor(() => {
        expect(screen.getByText('http://order-service:8080')).toBeInTheDocument()
      })
    })

    it('отображает HTTP methods как tags', async () => {
      renderWithMockAuth(<RouteDetailsPage />, {
        authValue: { isAuthenticated: true, user: mockUser },
        initialEntries: ['/routes/route-1'],
      })

      await waitFor(() => {
        expect(screen.getByText('GET')).toBeInTheDocument()
        expect(screen.getByText('POST')).toBeInTheDocument()
      })
    })

    it('отображает описание маршрута', async () => {
      renderWithMockAuth(<RouteDetailsPage />, {
        authValue: { isAuthenticated: true, user: mockUser },
        initialEntries: ['/routes/route-1'],
      })

      await waitFor(() => {
        expect(screen.getByText('Order service')).toBeInTheDocument()
      })
    })

    it('отображает автора маршрута', async () => {
      renderWithMockAuth(<RouteDetailsPage />, {
        authValue: { isAuthenticated: true, user: mockUser },
        initialEntries: ['/routes/route-1'],
      })

      await waitFor(() => {
        expect(screen.getByText('testuser')).toBeInTheDocument()
      })
    })

    it('отображает даты создания и обновления', async () => {
      renderWithMockAuth(<RouteDetailsPage />, {
        authValue: { isAuthenticated: true, user: mockUser },
        initialEntries: ['/routes/route-1'],
      })

      await waitFor(() => {
        // Проверяем наличие labels для дат
        expect(screen.getByText('Создан')).toBeInTheDocument()
        expect(screen.getByText('Обновлён')).toBeInTheDocument()
      })
    })
  })

  describe('кнопка Edit для draft маршрутов (AC: #2, #3)', () => {
    it('показывает кнопку Редактировать для draft маршрута владельца', async () => {
      mockRouteData = { ...mockRoute, status: 'draft', createdBy: 'user-1' }
      mockUser = { userId: 'user-1', username: 'testuser', role: 'developer' }

      renderWithMockAuth(<RouteDetailsPage />, {
        authValue: { isAuthenticated: true, user: mockUser },
        initialEntries: ['/routes/route-1'],
      })

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /редактировать/i })).toBeInTheDocument()
      })
    })

    it('скрывает кнопку Редактировать для non-draft маршрута', async () => {
      mockRouteData = { ...mockRoute, status: 'published' }

      renderWithMockAuth(<RouteDetailsPage />, {
        authValue: { isAuthenticated: true, user: mockUser },
        initialEntries: ['/routes/route-1'],
      })

      await waitFor(() => {
        expect(screen.getByText('/api/orders')).toBeInTheDocument()
      })

      expect(screen.queryByRole('button', { name: /редактировать/i })).not.toBeInTheDocument()
    })

    it('скрывает кнопку Редактировать для draft маршрута чужого владельца', async () => {
      mockRouteData = { ...mockRoute, status: 'draft', createdBy: 'other-user' }
      mockUser = { userId: 'user-1', username: 'testuser', role: 'developer' }

      renderWithMockAuth(<RouteDetailsPage />, {
        authValue: { isAuthenticated: true, user: mockUser },
        initialEntries: ['/routes/route-1'],
      })

      await waitFor(() => {
        expect(screen.getByText('/api/orders')).toBeInTheDocument()
      })

      expect(screen.queryByRole('button', { name: /редактировать/i })).not.toBeInTheDocument()
    })

    it('навигирует на страницу редактирования при клике Edit', async () => {
      mockRouteData = { ...mockRoute, status: 'draft', createdBy: 'user-1' }
      mockUser = { userId: 'user-1', username: 'testuser', role: 'developer' }

      renderWithMockAuth(<RouteDetailsPage />, {
        authValue: { isAuthenticated: true, user: mockUser },
        initialEntries: ['/routes/route-1'],
      })

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /редактировать/i })).toBeInTheDocument()
      })

      fireEvent.click(screen.getByRole('button', { name: /редактировать/i }))

      expect(mockNavigate).toHaveBeenCalledWith('/routes/route-1/edit')
    })
  })

  describe('клонирование маршрута (AC: #4)', () => {
    it('отображает кнопку Клонировать', async () => {
      renderWithMockAuth(<RouteDetailsPage />, {
        authValue: { isAuthenticated: true, user: mockUser },
        initialEntries: ['/routes/route-1'],
      })

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /клонировать/i })).toBeInTheDocument()
      })
    })

    it('клонирует маршрут и редиректит на страницу редактирования клона', async () => {
      const clonedRoute = { ...mockRoute, id: 'cloned-route-id' }
      mockCloneMutateAsync = vi.fn().mockResolvedValue(clonedRoute)

      renderWithMockAuth(<RouteDetailsPage />, {
        authValue: { isAuthenticated: true, user: mockUser },
        initialEntries: ['/routes/route-1'],
      })

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /клонировать/i })).toBeInTheDocument()
      })

      fireEvent.click(screen.getByRole('button', { name: /клонировать/i }))

      await waitFor(() => {
        expect(mockCloneMutateAsync).toHaveBeenCalledWith('route-1')
      })

      await waitFor(() => {
        expect(mockNavigate).toHaveBeenCalledWith('/routes/cloned-route-id/edit')
      })
    })

    it('показывает loading состояние при клонировании', async () => {
      mockCloneIsPending = true

      renderWithMockAuth(<RouteDetailsPage />, {
        authValue: { isAuthenticated: true, user: mockUser },
        initialEntries: ['/routes/route-1'],
      })

      await waitFor(() => {
        const cloneButton = screen.getByRole('button', { name: /клонировать/i })
        expect(cloneButton).toHaveClass('ant-btn-loading')
      })
    })

    it('обрабатывает ошибку клонирования без падения', async () => {
      // Мокаем ошибку клонирования
      mockCloneMutateAsync = vi.fn().mockRejectedValue(new Error('Clone failed'))

      renderWithMockAuth(<RouteDetailsPage />, {
        authValue: { isAuthenticated: true, user: mockUser },
        initialEntries: ['/routes/route-1'],
      })

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /клонировать/i })).toBeInTheDocument()
      })

      fireEvent.click(screen.getByRole('button', { name: /клонировать/i }))

      // Проверяем что mutateAsync был вызван
      await waitFor(() => {
        expect(mockCloneMutateAsync).toHaveBeenCalledWith('route-1')
      })

      // Проверяем что navigate НЕ был вызван (из-за ошибки)
      expect(mockNavigate).not.toHaveBeenCalled()

      // Страница должна остаться без изменений
      expect(screen.getByText('/api/orders')).toBeInTheDocument()
    })
  })

  describe('отображение Rate Limit (Story 5.5)', () => {
    it('отображает rate limit информацию если назначен', async () => {
      // Story 5.5: маршрут с полными данными rate limit
      mockRouteData = {
        ...mockRoute,
        rateLimitId: 'rate-limit-1',
        rateLimit: {
          id: 'rate-limit-1',
          name: 'standard',
          requestsPerSecond: 100,
          burstSize: 150,
        },
      }

      renderWithMockAuth(<RouteDetailsPage />, {
        authValue: { isAuthenticated: true, user: mockUser },
        initialEntries: ['/routes/route-1'],
      })

      await waitFor(() => {
        expect(screen.getByText('Rate Limit Policy')).toBeInTheDocument()
        expect(screen.getByText('standard')).toBeInTheDocument()
        expect(screen.getByText('100')).toBeInTheDocument()
        expect(screen.getByText('150')).toBeInTheDocument()
      })
    })

    it('отображает сообщение "No rate limiting configured" если не назначен', async () => {
      // Story 5.5: маршрут без rate limit
      mockRouteData = { ...mockRoute, rateLimitId: null, rateLimit: null }

      renderWithMockAuth(<RouteDetailsPage />, {
        authValue: { isAuthenticated: true, user: mockUser },
        initialEntries: ['/routes/route-1'],
      })

      await waitFor(() => {
        expect(screen.getByText('/api/orders')).toBeInTheDocument()
      })

      // Story 5.5: секция Rate Limit всегда отображается, но с сообщением о её отсутствии
      expect(screen.getByText('No rate limiting configured')).toBeInTheDocument()
      expect(screen.getByText('Consider adding rate limiting for production routes')).toBeInTheDocument()
    })
  })

  describe('обработка несуществующего маршрута (AC: #6)', () => {
    it('показывает 404 страницу для несуществующего маршрута', async () => {
      mockRouteData = undefined
      mockRouteError = new Error('Not found')

      renderWithMockAuth(<RouteDetailsPage />, {
        authValue: { isAuthenticated: true, user: mockUser },
        initialEntries: ['/routes/nonexistent'],
      })

      await waitFor(() => {
        expect(screen.getByText('Маршрут не найден')).toBeInTheDocument()
      })

      expect(screen.getByText('Маршрут с указанным ID не существует')).toBeInTheDocument()
    })

    it('отображает кнопку возврата к списку на 404 странице', async () => {
      mockRouteData = undefined
      mockRouteError = new Error('Not found')

      renderWithMockAuth(<RouteDetailsPage />, {
        authValue: { isAuthenticated: true, user: mockUser },
        initialEntries: ['/routes/nonexistent'],
      })

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /вернуться к списку/i })).toBeInTheDocument()
      })
    })

    it('навигирует к списку маршрутов при клике на кнопку возврата', async () => {
      mockRouteData = undefined
      mockRouteError = new Error('Not found')

      renderWithMockAuth(<RouteDetailsPage />, {
        authValue: { isAuthenticated: true, user: mockUser },
        initialEntries: ['/routes/nonexistent'],
      })

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /вернуться к списку/i })).toBeInTheDocument()
      })

      fireEvent.click(screen.getByRole('button', { name: /вернуться к списку/i }))

      expect(mockNavigate).toHaveBeenCalledWith('/routes')
    })
  })

  describe('навигация', () => {
    it('навигирует назад к списку при клике кнопки Назад', async () => {
      renderWithMockAuth(<RouteDetailsPage />, {
        authValue: { isAuthenticated: true, user: mockUser },
        initialEntries: ['/routes/route-1'],
      })

      await waitFor(() => {
        expect(screen.getByRole('button', { name: /назад/i })).toBeInTheDocument()
      })

      fireEvent.click(screen.getByRole('button', { name: /назад/i }))

      expect(mockNavigate).toHaveBeenCalledWith('/routes')
    })
  })

  describe('состояние загрузки', () => {
    it('показывает spinner во время загрузки', async () => {
      mockIsLoadingRoute = true
      mockRouteData = undefined

      renderWithMockAuth(<RouteDetailsPage />, {
        authValue: { isAuthenticated: true, user: mockUser },
        initialEntries: ['/routes/route-1'],
      })

      // Spinner от Ant Design имеет класс ant-spin
      expect(document.querySelector('.ant-spin')).toBeInTheDocument()
    })
  })

  describe('различные статусы маршрута', () => {
    it('отображает статус "На согласовании" для pending маршрута', async () => {
      mockRouteData = { ...mockRoute, status: 'pending' }

      renderWithMockAuth(<RouteDetailsPage />, {
        authValue: { isAuthenticated: true, user: mockUser },
        initialEntries: ['/routes/route-1'],
      })

      await waitFor(() => {
        expect(screen.getByText('На согласовании')).toBeInTheDocument()
      })
    })

    it('отображает статус "Опубликован" для published маршрута', async () => {
      mockRouteData = { ...mockRoute, status: 'published' }

      renderWithMockAuth(<RouteDetailsPage />, {
        authValue: { isAuthenticated: true, user: mockUser },
        initialEntries: ['/routes/route-1'],
      })

      await waitFor(() => {
        expect(screen.getByText('Опубликован')).toBeInTheDocument()
      })
    })

    it('отображает статус "Отклонён" для rejected маршрута', async () => {
      mockRouteData = { ...mockRoute, status: 'rejected' }

      renderWithMockAuth(<RouteDetailsPage />, {
        authValue: { isAuthenticated: true, user: mockUser },
        initialEntries: ['/routes/route-1'],
      })

      await waitFor(() => {
        expect(screen.getByText('Отклонён')).toBeInTheDocument()
      })
    })
  })
})
