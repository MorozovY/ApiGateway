// Тесты для Submit for Approval UI (Story 4.5), Rate Limit секция (Story 5.5)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { screen, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { renderWithMockAuth } from '../../../test/test-utils'
import { RouteDetailsCard } from './RouteDetailsCard'
import type { Route } from '../types/route.types'

// Мок navigate функция
const mockNavigate = vi.fn()

// Мок react-router-dom
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

// Мок данные — базовый draft маршрут владельца
const mockDraftRoute: Route = {
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

// Моки для hooks — будем устанавливать значение перед каждым тестом
let mockSubmitMutateAsync = vi.fn()
let mockSubmitIsPending = false
let mockCloneMutateAsync = vi.fn()
let mockCloneIsPending = false

vi.mock('../hooks/useRoutes', () => ({
  useCloneRoute: () => ({
    mutateAsync: mockCloneMutateAsync,
    isPending: mockCloneIsPending,
    reset: vi.fn(),
  }),
  useSubmitRoute: () => ({
    mutateAsync: mockSubmitMutateAsync,
    isPending: mockSubmitIsPending,
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

describe('Submit for Approval UI', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockSubmitMutateAsync = vi.fn()
    mockSubmitIsPending = false
    mockCloneMutateAsync = vi.fn()
    mockCloneIsPending = false
    mockUser = { userId: 'user-1', username: 'testuser', role: 'developer' }
  })

  afterEach(() => {
    cleanup()
  })

  it('отображает кнопку "Отправить на согласование" для draft маршрута владельца', async () => {
    renderWithMockAuth(<RouteDetailsCard route={mockDraftRoute} />, {
      authValue: { isAuthenticated: true, user: mockUser },
    })

    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: /отправить на согласование/i })
      ).toBeInTheDocument()
    })
  })

  it('скрывает кнопку submit для non-draft маршрута', async () => {
    const publishedRoute = { ...mockDraftRoute, status: 'published' as const }

    renderWithMockAuth(<RouteDetailsCard route={publishedRoute} />, {
      authValue: { isAuthenticated: true, user: mockUser },
    })

    await waitFor(() => {
      expect(screen.getByText('/api/orders')).toBeInTheDocument()
    })

    expect(
      screen.queryByRole('button', { name: /отправить на согласование/i })
    ).not.toBeInTheDocument()
  })

  it('скрывает кнопку submit если маршрут принадлежит другому пользователю', async () => {
    const otherOwnerRoute = { ...mockDraftRoute, createdBy: 'other-user' }

    renderWithMockAuth(<RouteDetailsCard route={otherOwnerRoute} />, {
      authValue: { isAuthenticated: true, user: mockUser },
    })

    await waitFor(() => {
      expect(screen.getByText('/api/orders')).toBeInTheDocument()
    })

    expect(
      screen.queryByRole('button', { name: /отправить на согласование/i })
    ).not.toBeInTheDocument()
  })

  it('открывает модальное окно при клике на кнопку submit', async () => {
    renderWithMockAuth(<RouteDetailsCard route={mockDraftRoute} />, {
      authValue: { isAuthenticated: true, user: mockUser },
    })

    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: /отправить на согласование/i })
      ).toBeInTheDocument()
    })

    fireEvent.click(screen.getByRole('button', { name: /отправить на согласование/i }))

    await waitFor(() => {
      // Текст предупреждения в модальном окне (уникален — не повторяется в кнопке)
      expect(
        screen.getByText(/маршрут будет отправлен в security на проверку/i)
      ).toBeInTheDocument()
    })
  })

  it('вызывает API при подтверждении и закрывает modal', async () => {
    mockSubmitMutateAsync = vi.fn().mockResolvedValue({ ...mockDraftRoute, status: 'pending' })

    renderWithMockAuth(<RouteDetailsCard route={mockDraftRoute} />, {
      authValue: { isAuthenticated: true, user: mockUser },
    })

    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: /отправить на согласование/i })
      ).toBeInTheDocument()
    })

    // Открываем модальное окно
    fireEvent.click(screen.getByRole('button', { name: /отправить на согласование/i }))

    await waitFor(() => {
      // Ждём появления текста предупреждения в модальном окне
      expect(
        screen.getByText(/маршрут будет отправлен в security на проверку/i)
      ).toBeInTheDocument()
    })

    // Кнопка "Отправить" в footer modal — точный текст отличается от триггера "Отправить на согласование"
    fireEvent.click(screen.getByRole('button', { name: /^отправить$/i }))

    await waitFor(() => {
      expect(mockSubmitMutateAsync).toHaveBeenCalledWith('route-1')
    })

    // Проверяем закрытие modal после успешного submit (destroyOnClose удаляет контент из DOM)
    await waitFor(() => {
      expect(
        screen.queryByText(/маршрут будет отправлен в security на проверку/i)
      ).not.toBeInTheDocument()
    })
  })

  it('показывает "Ожидает одобрения Security" для pending маршрута владельца', async () => {
    const pendingRoute = { ...mockDraftRoute, status: 'pending' as const }

    renderWithMockAuth(<RouteDetailsCard route={pendingRoute} />, {
      authValue: { isAuthenticated: true, user: mockUser },
    })

    await waitFor(() => {
      expect(screen.getByText('Ожидает одобрения Security')).toBeInTheDocument()
    })
  })

  it('не показывает кнопку Edit для pending маршрута владельца', async () => {
    const pendingRoute = { ...mockDraftRoute, status: 'pending' as const }

    renderWithMockAuth(<RouteDetailsCard route={pendingRoute} />, {
      authValue: { isAuthenticated: true, user: mockUser },
    })

    await waitFor(() => {
      expect(screen.getByText('Ожидает одобрения Security')).toBeInTheDocument()
    })

    // Кнопка "Редактировать" (обычный Edit) не должна отображаться для pending
    expect(screen.queryByRole('button', { name: /^редактировать$/i })).not.toBeInTheDocument()
    // Кнопка submit тоже не должна быть
    expect(
      screen.queryByRole('button', { name: /отправить на согласование/i })
    ).not.toBeInTheDocument()
  })

  it('показывает причину отклонения для rejected маршрута владельца', async () => {
    const rejectedRoute = {
      ...mockDraftRoute,
      status: 'rejected' as const,
      rejectionReason: 'Неверный upstream URL',
      rejectorUsername: 'admin',
    }

    renderWithMockAuth(<RouteDetailsCard route={rejectedRoute} />, {
      authValue: { isAuthenticated: true, user: mockUser },
    })

    await waitFor(() => {
      expect(screen.getByText('Маршрут отклонён')).toBeInTheDocument()
      expect(screen.getByText(/неверный upstream url/i)).toBeInTheDocument()
      expect(screen.getByText(/admin/i)).toBeInTheDocument()
    })
  })

  it('показывает кнопку "Редактировать и повторно отправить" для rejected маршрута владельца', async () => {
    const rejectedRoute = {
      ...mockDraftRoute,
      status: 'rejected' as const,
      rejectionReason: 'Неверный путь',
    }

    renderWithMockAuth(<RouteDetailsCard route={rejectedRoute} />, {
      authValue: { isAuthenticated: true, user: mockUser },
    })

    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: /редактировать и повторно отправить/i })
      ).toBeInTheDocument()
    })
  })

  it('навигирует на /edit при клике "Редактировать и повторно отправить"', async () => {
    const rejectedRoute = {
      ...mockDraftRoute,
      status: 'rejected' as const,
      rejectionReason: 'Неверный путь',
    }

    renderWithMockAuth(<RouteDetailsCard route={rejectedRoute} />, {
      authValue: { isAuthenticated: true, user: mockUser },
    })

    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: /редактировать и повторно отправить/i })
      ).toBeInTheDocument()
    })

    fireEvent.click(
      screen.getByRole('button', { name: /редактировать и повторно отправить/i })
    )

    expect(mockNavigate).toHaveBeenCalledWith('/routes/route-1/edit')
  })

  it('не показывает Alert "Ожидает одобрения" для pending маршрута не-владельца', async () => {
    const pendingOtherOwnerRoute = {
      ...mockDraftRoute,
      status: 'pending' as const,
      createdBy: 'other-user',
    }

    renderWithMockAuth(<RouteDetailsCard route={pendingOtherOwnerRoute} />, {
      authValue: { isAuthenticated: true, user: mockUser },
    })

    await waitFor(() => {
      expect(screen.getByText('/api/orders')).toBeInTheDocument()
    })

    // Alert "Ожидает одобрения" отображается только для владельца маршрута
    expect(screen.queryByText('Ожидает одобрения Security')).not.toBeInTheDocument()
  })

  it('не закрывает modal при ошибке submit', async () => {
    mockSubmitMutateAsync = vi.fn().mockRejectedValue(new Error('Submit failed'))

    renderWithMockAuth(<RouteDetailsCard route={mockDraftRoute} />, {
      authValue: { isAuthenticated: true, user: mockUser },
    })

    await waitFor(() => {
      expect(
        screen.getByRole('button', { name: /отправить на согласование/i })
      ).toBeInTheDocument()
    })

    // Открываем modal
    fireEvent.click(screen.getByRole('button', { name: /отправить на согласование/i }))

    await waitFor(() => {
      expect(
        screen.getByText(/маршрут будет отправлен в security на проверку/i)
      ).toBeInTheDocument()
    })

    // Нажимаем кнопку подтверждения
    fireEvent.click(screen.getByRole('button', { name: /^отправить$/i }))

    await waitFor(() => {
      expect(mockSubmitMutateAsync).toHaveBeenCalledWith('route-1')
    })

    // При ошибке modal должен остаться открытым — пользователь видит что отправка не прошла
    expect(
      screen.getByText(/маршрут будет отправлен в security на проверку/i)
    ).toBeInTheDocument()
  })
})

describe('секция Rate Limit (Story 5.5)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockSubmitMutateAsync = vi.fn()
    mockSubmitIsPending = false
    mockCloneMutateAsync = vi.fn()
    mockCloneIsPending = false
    mockUser = { userId: 'user-1', username: 'testuser', role: 'developer' }
  })

  afterEach(() => {
    cleanup()
  })

  // Маршрут с назначенной политикой rate limit
  const mockRouteWithRateLimit: Route = {
    ...mockDraftRoute,
    rateLimit: {
      id: 'policy-1',
      name: 'standard',
      requestsPerSecond: 100,
      burstSize: 150,
    },
  }

  // Маршрут без rate limit
  const mockRouteWithoutRateLimit: Route = {
    ...mockDraftRoute,
    rateLimitId: null,
    rateLimit: null,
  }

  it('отображает name, requestsPerSecond, burstSize когда политика назначена', async () => {
    renderWithMockAuth(<RouteDetailsCard route={mockRouteWithRateLimit} />, {
      authValue: { isAuthenticated: true, user: mockUser },
    })

    await waitFor(() => {
      // Проверяем отображение названия политики
      expect(screen.getByText('standard')).toBeInTheDocument()
      // Проверяем отображение requests per second
      expect(screen.getByText('100')).toBeInTheDocument()
      // Проверяем отображение burst size
      expect(screen.getByText('150')).toBeInTheDocument()
    })

    // Проверяем labels
    expect(screen.getByText('Rate Limit Policy')).toBeInTheDocument()
    expect(screen.getByText('Requests per Second')).toBeInTheDocument()
    expect(screen.getByText('Burst Size')).toBeInTheDocument()
  })

  it('отображает "No rate limiting configured" когда политика не назначена', async () => {
    renderWithMockAuth(<RouteDetailsCard route={mockRouteWithoutRateLimit} />, {
      authValue: { isAuthenticated: true, user: mockUser },
    })

    await waitFor(() => {
      expect(screen.getByText('No rate limiting configured')).toBeInTheDocument()
    })
  })

  it('показывает подсказку о добавлении rate limiting', async () => {
    renderWithMockAuth(<RouteDetailsCard route={mockRouteWithoutRateLimit} />, {
      authValue: { isAuthenticated: true, user: mockUser },
    })

    await waitFor(() => {
      expect(
        screen.getByText('Consider adding rate limiting for production routes')
      ).toBeInTheDocument()
    })
  })
})
