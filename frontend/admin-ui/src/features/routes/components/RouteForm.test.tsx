// Тесты для поля Rate Limit Policy в RouteForm (Story 5.5)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { screen, waitFor, cleanup } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithMockAuth } from '../../../test/test-utils'
import { RouteForm } from './RouteForm'
import type { Route } from '../types/route.types'

// Мок API модуля
vi.mock('../api/routesApi', () => ({
  checkPathExists: vi.fn().mockResolvedValue(false),
}))

// Мок данные rate limit политик
const mockRateLimitsData = {
  items: [
    { id: 'policy-1', name: 'standard', requestsPerSecond: 100, burstSize: 150, usageCount: 5, description: null, createdBy: 'admin', createdAt: '2026-02-18T10:00:00Z', updatedAt: '2026-02-18T10:00:00Z' },
    { id: 'policy-2', name: 'premium', requestsPerSecond: 1000, burstSize: 1500, usageCount: 2, description: null, createdBy: 'admin', createdAt: '2026-02-18T10:00:00Z', updatedAt: '2026-02-18T10:00:00Z' },
  ],
  total: 2,
  offset: 0,
  limit: 20,
}

// Мок useRateLimits hook
let mockRateLimitsLoading = false
vi.mock('@features/rate-limits', () => ({
  useRateLimits: () => ({
    data: mockRateLimitsData,
    isLoading: mockRateLimitsLoading,
    error: null,
  }),
}))

describe('поле Rate Limit Policy (Story 5.5)', () => {
  const mockOnSubmit = vi.fn()
  const mockOnCancel = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    mockRateLimitsLoading = false
    mockOnSubmit.mockResolvedValue(undefined)
  })

  afterEach(() => {
    cleanup()
  })

  it('отображает label поля Rate Limit Policy', () => {
    renderWithMockAuth(
      <RouteForm
        onSubmit={mockOnSubmit}
        onCancel={mockOnCancel}
        isSubmitting={false}
        mode="create"
      />,
      { authValue: { isAuthenticated: true } }
    )

    // Проверяем label поля
    expect(screen.getByText('Rate Limit Policy')).toBeInTheDocument()
  })

  it('отображает dropdown с политиками rate limit при клике', async () => {
    const user = userEvent.setup()

    renderWithMockAuth(
      <RouteForm
        onSubmit={mockOnSubmit}
        onCancel={mockOnCancel}
        isSubmitting={false}
        mode="create"
      />,
      { authValue: { isAuthenticated: true } }
    )

    // Находим и кликаем на Select
    const selectTrigger = screen.getByRole('combobox', { name: /rate limit policy/i })
    await user.click(selectTrigger)

    // Проверяем наличие опций в открывшемся dropdown (AC1: "None" + все доступные политики)
    await waitFor(() => {
      expect(screen.getByText('None')).toBeInTheDocument()
      expect(screen.getByText('standard (100/sec)')).toBeInTheDocument()
      expect(screen.getByText('premium (1000/sec)')).toBeInTheDocument()
    })
  })

  it('отображает "None" как первую опцию в dropdown', async () => {
    const user = userEvent.setup()

    renderWithMockAuth(
      <RouteForm
        onSubmit={mockOnSubmit}
        onCancel={mockOnCancel}
        isSubmitting={false}
        mode="create"
      />,
      { authValue: { isAuthenticated: true } }
    )

    // Находим и кликаем на Select
    const selectTrigger = screen.getByRole('combobox', { name: /rate limit policy/i })
    await user.click(selectTrigger)

    // "None" должен быть доступен в dropdown (AC3: опция для отсутствия rate limit)
    await waitFor(() => {
      expect(screen.getByText('None')).toBeInTheDocument()
    })
  })

  it('позволяет выбрать политику rate limit и отображает её в поле', async () => {
    const user = userEvent.setup()

    renderWithMockAuth(
      <RouteForm
        onSubmit={mockOnSubmit}
        onCancel={mockOnCancel}
        isSubmitting={false}
        mode="create"
      />,
      { authValue: { isAuthenticated: true } }
    )

    // Находим и кликаем на Select
    const selectTrigger = screen.getByRole('combobox', { name: /rate limit policy/i })
    await user.click(selectTrigger)

    // Выбираем политику (AC2: выбор политики для назначения маршруту)
    await waitFor(() => {
      expect(screen.getByText('standard (100/sec)')).toBeInTheDocument()
    })
    await user.click(screen.getByText('standard (100/sec)'))

    // Проверяем что политика выбрана и отображается в поле
    await waitFor(() => {
      const elements = screen.getAllByTitle('standard (100/sec)')
      expect(elements.length).toBeGreaterThan(0)
    })
  })

  it('формат опций соответствует требованиям: name (requests/sec)', async () => {
    const user = userEvent.setup()

    renderWithMockAuth(
      <RouteForm
        onSubmit={mockOnSubmit}
        onCancel={mockOnCancel}
        isSubmitting={false}
        mode="create"
      />,
      { authValue: { isAuthenticated: true } }
    )

    const selectTrigger = screen.getByRole('combobox', { name: /rate limit policy/i })
    await user.click(selectTrigger)

    // Проверяем формат: "name (requestsPerSecond/sec)" (AC1)
    await waitFor(() => {
      expect(screen.getByText('standard (100/sec)')).toBeInTheDocument()
      expect(screen.getByText('premium (1000/sec)')).toBeInTheDocument()
    })
  })

  it('инициализирует форму с текущей политикой rate limit в режиме редактирования', async () => {
    // Маршрут с назначенной политикой rate limit
    const routeWithRateLimit: Route = {
      id: 'route-1',
      path: '/api/orders',
      upstreamUrl: 'http://order-service:8080',
      methods: ['GET', 'POST'],
      description: 'Order service',
      status: 'draft',
      createdBy: 'user-1',
      createdAt: '2026-02-18T10:00:00Z',
      updatedAt: '2026-02-18T10:00:00Z',
      rateLimitId: 'policy-2',
      rateLimit: {
        id: 'policy-2',
        name: 'premium',
        requestsPerSecond: 1000,
        burstSize: 1500,
      },
    }

    renderWithMockAuth(
      <RouteForm
        initialValues={routeWithRateLimit}
        onSubmit={mockOnSubmit}
        onCancel={mockOnCancel}
        isSubmitting={false}
        mode="edit"
      />,
      { authValue: { isAuthenticated: true } }
    )

    // Проверяем что select инициализирован с правильной политикой (режим редактирования)
    await waitFor(() => {
      const elements = screen.getAllByTitle('premium (1000/sec)')
      expect(elements.length).toBeGreaterThan(0)
    })
  })

  it('инициализирует форму без политики когда rateLimitId отсутствует', () => {
    // Маршрут без rate limit
    const routeWithoutRateLimit: Route = {
      id: 'route-1',
      path: '/api/orders',
      upstreamUrl: 'http://order-service:8080',
      methods: ['GET', 'POST'],
      description: 'Order service',
      status: 'draft',
      createdBy: 'user-1',
      createdAt: '2026-02-18T10:00:00Z',
      updatedAt: '2026-02-18T10:00:00Z',
      rateLimitId: null,
      rateLimit: null,
    }

    renderWithMockAuth(
      <RouteForm
        initialValues={routeWithoutRateLimit}
        onSubmit={mockOnSubmit}
        onCancel={mockOnCancel}
        isSubmitting={false}
        mode="edit"
      />,
      { authValue: { isAuthenticated: true } }
    )

    // Проверяем что поле не имеет выбранного значения (placeholder отображается)
    const selectTrigger = screen.getByRole('combobox', { name: /rate limit policy/i })
    expect(selectTrigger).toBeInTheDocument()
    // В Ant Design Select без значения показывается placeholder
    expect(screen.getByText('Выберите политику (опционально)')).toBeInTheDocument()
  })
})
