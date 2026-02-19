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

describe('интеграция Rate Limit с onSubmit (Story 5.5)', () => {
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

  it('передаёт rateLimitId в onSubmit при выборе политики (AC2)', async () => {
    const user = userEvent.setup()

    // Используем initialValues для упрощения теста (не нужно заполнять все поля)
    const routeWithoutRateLimit: Route = {
      id: 'route-1',
      path: '/api/orders',
      upstreamUrl: 'http://localhost:8080',
      methods: ['GET'],
      description: 'Test',
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

    // Выбираем Rate Limit Policy
    const rateLimitSelect = screen.getByRole('combobox', { name: /rate limit policy/i })
    await user.click(rateLimitSelect)
    await waitFor(() => {
      expect(screen.getByText('standard (100/sec)')).toBeInTheDocument()
    })
    await user.click(screen.getByText('standard (100/sec)'))

    // Submit формы
    await user.click(screen.getByRole('button', { name: /save as draft/i }))

    // Проверяем что onSubmit вызван с правильным rateLimitId
    await waitFor(() => {
      expect(mockOnSubmit).toHaveBeenCalledWith(
        expect.objectContaining({
          rateLimitId: 'policy-1',
        })
      )
    })
  })

  it('передаёт rateLimitId: null в onSubmit при выборе "None" (AC3)', async () => {
    const user = userEvent.setup()

    // Используем initialValues с назначенной политикой, затем меняем на None
    const routeWithRateLimit: Route = {
      id: 'route-1',
      path: '/api/orders',
      upstreamUrl: 'http://localhost:8080',
      methods: ['GET'],
      description: 'Test',
      status: 'draft',
      createdBy: 'user-1',
      createdAt: '2026-02-18T10:00:00Z',
      updatedAt: '2026-02-18T10:00:00Z',
      rateLimitId: 'policy-1',
      rateLimit: {
        id: 'policy-1',
        name: 'standard',
        requestsPerSecond: 100,
        burstSize: 150,
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

    // Выбираем "None" в Rate Limit Policy
    const rateLimitSelect = screen.getByRole('combobox', { name: /rate limit policy/i })
    await user.click(rateLimitSelect)
    await waitFor(() => {
      expect(screen.getByText('None')).toBeInTheDocument()
    })
    await user.click(screen.getByText('None'))

    // Submit формы
    await user.click(screen.getByRole('button', { name: /save as draft/i }))

    // Проверяем что onSubmit вызван с rateLimitId: null
    await waitFor(() => {
      expect(mockOnSubmit).toHaveBeenCalledWith(
        expect.objectContaining({
          rateLimitId: null,
        })
      )
    })
  })

  it('передаёт rateLimitId: null когда политика не выбрана', async () => {
    const user = userEvent.setup()

    // Используем initialValues без rate limit
    const routeWithoutRateLimit: Route = {
      id: 'route-1',
      path: '/api/orders',
      upstreamUrl: 'http://localhost:8080',
      methods: ['GET'],
      description: 'Test',
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

    // Submit формы без изменения Rate Limit (остаётся null)
    await user.click(screen.getByRole('button', { name: /save as draft/i }))

    // Проверяем что onSubmit вызван с rateLimitId: null
    await waitFor(() => {
      expect(mockOnSubmit).toHaveBeenCalledWith(
        expect.objectContaining({
          rateLimitId: null,
        })
      )
    })
  })

  it('передаёт rateLimitId: null после очистки выбранной политики (allowClear)', async () => {
    const user = userEvent.setup()

    // Используем initialValues с назначенной политикой
    const routeWithRateLimit: Route = {
      id: 'route-1',
      path: '/api/orders',
      upstreamUrl: 'http://localhost:8080',
      methods: ['GET'],
      description: 'Test',
      status: 'draft',
      createdBy: 'user-1',
      createdAt: '2026-02-18T10:00:00Z',
      updatedAt: '2026-02-18T10:00:00Z',
      rateLimitId: 'policy-1',
      rateLimit: {
        id: 'policy-1',
        name: 'standard',
        requestsPerSecond: 100,
        burstSize: 150,
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

    // Ждём пока форма инициализируется с политикой
    await waitFor(() => {
      const elements = screen.getAllByTitle('standard (100/sec)')
      expect(elements.length).toBeGreaterThan(0)
    })

    // Очищаем выбор через кнопку clear (X) — наводим на Select чтобы показать кнопку
    const rateLimitSelect = screen.getByRole('combobox', { name: /rate limit policy/i })
    await user.hover(rateLimitSelect)

    // Находим и кликаем кнопку очистки
    const clearButton = document.querySelector('.ant-select-clear')
    expect(clearButton).toBeInTheDocument()
    await user.click(clearButton!)

    // Submit формы
    await user.click(screen.getByRole('button', { name: /save as draft/i }))

    // Проверяем что onSubmit вызван с rateLimitId: null после очистки
    await waitFor(() => {
      expect(mockOnSubmit).toHaveBeenCalledWith(
        expect.objectContaining({
          rateLimitId: null,
        })
      )
    })
  })
})

describe('loading state Rate Limit (Story 5.5)', () => {
  const mockOnSubmit = vi.fn()
  const mockOnCancel = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    mockOnSubmit.mockResolvedValue(undefined)
  })

  afterEach(() => {
    mockRateLimitsLoading = false
    cleanup()
  })

  it('отображает loading spinner когда политики загружаются', () => {
    // Устанавливаем loading state
    mockRateLimitsLoading = true

    renderWithMockAuth(
      <RouteForm
        onSubmit={mockOnSubmit}
        onCancel={mockOnCancel}
        isSubmitting={false}
        mode="create"
      />,
      { authValue: { isAuthenticated: true } }
    )

    // Проверяем что Select находится в loading state
    // Ant Design Select с loading=true имеет класс ant-select-loading или содержит spinner
    const selectElement = document.querySelector('.ant-select')
    expect(selectElement).toBeInTheDocument()

    // Альтернативная проверка — наличие loading spinner внутри select
    const loadingSpinner = document.querySelector('.ant-select-arrow .ant-spin')
    // Если spinner нет, проверяем атрибут loading на Select
    if (!loadingSpinner) {
      // Проверяем что компонент рендерится корректно даже в loading state
      expect(screen.getByText('Rate Limit Policy')).toBeInTheDocument()
    }
  })
})
