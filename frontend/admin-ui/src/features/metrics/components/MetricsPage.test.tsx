// Тесты для MetricsPage (Story 6.5, 8.1)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import type { ReactNode } from 'react'
import MetricsPage from './MetricsPage'
import { AuthContext } from '@features/auth'
import type { AuthContextType, User } from '@features/auth'
import * as metricsApi from '../api/metricsApi'
import * as healthApi from '../api/healthApi'
import type { MetricsSummary, TopRoute, HealthResponse } from '../types/metrics.types'

// Мокаем API
vi.mock('../api/metricsApi', () => ({
  getSummary: vi.fn(),
  getTopRoutes: vi.fn(),
}))

vi.mock('../api/healthApi', () => ({
  getServicesHealth: vi.fn(),
}))

// Мокаем ThemeProvider для HealthCheckSection
vi.mock('@/shared/providers/ThemeProvider', () => ({
  useThemeContext: () => ({
    theme: 'light',
    isDark: false,
    isLight: true,
    toggle: vi.fn(),
    setTheme: vi.fn(),
  }),
}))

const mockGetSummary = metricsApi.getSummary as ReturnType<typeof vi.fn>
const mockGetTopRoutes = metricsApi.getTopRoutes as ReturnType<typeof vi.fn>
const mockGetServicesHealth = healthApi.getServicesHealth as ReturnType<typeof vi.fn>

// Мок AuthContext для тестов с разными ролями
const mockAdminUser: User = { userId: 'admin-1', username: 'admin', role: 'admin' }
const mockDeveloperUser: User = { userId: 'dev-1', username: 'developer', role: 'developer' }

const createMockAuthContext = (user: User | null = mockAdminUser): AuthContextType => ({
  user,
  isAuthenticated: user !== null,
  isLoading: false,
  error: null,
  login: vi.fn(),
  logout: vi.fn(),
  clearError: vi.fn(),
})

// Тестовые данные
const mockSummary: MetricsSummary = {
  period: '5m',
  totalRequests: 12500,
  requestsPerSecond: 41.7,
  avgLatencyMs: 45,
  p95LatencyMs: 120,
  p99LatencyMs: 250,
  errorRate: 0.02,
  errorCount: 250,
  activeRoutes: 45,
}

const mockTopRoutes: TopRoute[] = [
  {
    routeId: 'route-1',
    path: '/api/orders',
    value: 15.2,
    metric: 'requests',
  },
  {
    routeId: 'route-2',
    path: '/api/users',
    value: 10.5,
    metric: 'requests',
  },
]

// Mock health response для Story 8.1 (6 сервисов: 4 из AC + prometheus + grafana)
const mockHealthResponse: HealthResponse = {
  services: [
    { name: 'gateway-core', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
    { name: 'gateway-admin', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
    { name: 'postgresql', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
    { name: 'redis', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
    { name: 'prometheus', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
    { name: 'grafana', status: 'UP', lastCheck: '2026-02-21T10:30:00Z', details: null },
  ],
  timestamp: '2026-02-21T10:30:00Z',
}

// Wrapper для тестов с поддержкой AuthContext
function createWrapper(authContext: AuthContextType = createMockAuthContext()) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <AuthContext.Provider value={authContext}>
          <BrowserRouter>{children}</BrowserRouter>
        </AuthContext.Provider>
      </QueryClientProvider>
    )
  }
}

describe('MetricsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetSummary.mockResolvedValue(mockSummary)
    mockGetTopRoutes.mockResolvedValue(mockTopRoutes)
    mockGetServicesHealth.mockResolvedValue(mockHealthResponse)
  })

  it('отображает loading состояние', async () => {
    let resolvePromise: (value: MetricsSummary) => void
    mockGetSummary.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolvePromise = resolve
        })
    )

    render(<MetricsPage />, { wrapper: createWrapper() })

    expect(screen.getByTestId('metrics-page-loading')).toBeInTheDocument()
    expect(screen.getByText('Loading metrics...')).toBeInTheDocument()

    // Резолвим чтобы тест не зависал
    resolvePromise!(mockSummary)
  })

  it('отображает страницу метрик после загрузки', async () => {
    render(<MetricsPage />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByTestId('metrics-page')).toBeInTheDocument()
    })

    // Проверяем summary cards
    expect(screen.getByTestId('summary-card-total-requests')).toBeInTheDocument()
    expect(screen.getByTestId('summary-card-rps')).toBeInTheDocument()
    expect(screen.getByTestId('summary-card-avg-latency')).toBeInTheDocument()
    expect(screen.getByTestId('summary-card-p95')).toBeInTheDocument()
    expect(screen.getByTestId('summary-card-error-rate')).toBeInTheDocument()
    expect(screen.getByTestId('summary-card-active-routes')).toBeInTheDocument()

    // Проверяем time range selector
    expect(screen.getByTestId('time-range-selector')).toBeInTheDocument()

    // Проверяем кнопку Grafana
    expect(screen.getByTestId('open-grafana-button')).toBeInTheDocument()

    // Проверяем таблицу топ-маршрутов
    expect(screen.getByTestId('top-routes-table')).toBeInTheDocument()
    expect(screen.getByText('/api/orders')).toBeInTheDocument()
    expect(screen.getByText('/api/users')).toBeInTheDocument()
  })

  it('отображает error состояние при ошибке API', async () => {
    mockGetSummary.mockRejectedValue(new Error('Network error'))

    render(<MetricsPage />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByTestId('metrics-page-error')).toBeInTheDocument()
    })

    expect(screen.getByText('Metrics unavailable')).toBeInTheDocument()
    expect(screen.getByTestId('metrics-page-retry-button')).toBeInTheDocument()
  })

  it('кнопка Open in Grafana имеет правильный URL', async () => {
    render(<MetricsPage />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByTestId('metrics-page')).toBeInTheDocument()
    })

    const grafanaButton = screen.getByTestId('open-grafana-button')
    expect(grafanaButton).toHaveAttribute(
      'href',
      'http://localhost:3001/d/api-gateway-dashboard/api-gateway'
    )
    expect(grafanaButton).toHaveAttribute('target', '_blank')
  })

  it('позволяет изменять time range', async () => {
    render(<MetricsPage />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByTestId('metrics-page')).toBeInTheDocument()
    })

    // По умолчанию должен быть выбран 5m
    expect(mockGetSummary).toHaveBeenCalledWith('5m')

    // Кликаем на 1h
    const segmented = screen.getByTestId('time-range-selector')
    const oneHourOption = segmented.querySelector('[title="1h"]') || screen.getByText('1h')
    fireEvent.click(oneHourOption)

    await waitFor(() => {
      expect(mockGetSummary).toHaveBeenCalledWith('1h')
    })
  })

  it('отображает Top Routes table с данными', async () => {
    render(<MetricsPage />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByTestId('metrics-page')).toBeInTheDocument()
    })

    // Проверяем что таблица отображается (заголовки колонок)
    expect(screen.getByText('Path')).toBeInTheDocument()
    // RPS есть в нескольких местах - в summary card и в таблице
    expect(screen.getAllByText('RPS').length).toBeGreaterThan(0)
  })

  // AC6: Тесты для developer роли
  describe('Role-based access (AC6)', () => {
    it('не показывает notice для admin роли', async () => {
      const adminAuth = createMockAuthContext(mockAdminUser)
      render(<MetricsPage />, { wrapper: createWrapper(adminAuth) })

      await waitFor(() => {
        expect(screen.getByTestId('metrics-page')).toBeInTheDocument()
      })

      // Notice для developer не должен отображаться для admin
      expect(screen.queryByTestId('developer-routes-notice')).not.toBeInTheDocument()
    })

    it('показывает notice для developer роли', async () => {
      const developerAuth = createMockAuthContext(mockDeveloperUser)
      render(<MetricsPage />, { wrapper: createWrapper(developerAuth) })

      await waitFor(() => {
        expect(screen.getByTestId('metrics-page')).toBeInTheDocument()
      })

      // Developer видит notice что показаны только его маршруты
      expect(screen.getByTestId('developer-routes-notice')).toBeInTheDocument()
      expect(screen.getByText('Showing only routes you created')).toBeInTheDocument()
    })
  })
})
