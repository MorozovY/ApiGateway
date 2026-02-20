// Тесты для DashboardPage (Story 6.5 — интеграция MetricsWidget)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import type { ReactNode } from 'react'
import { DashboardPage } from './DashboardPage'
import { AuthContext } from '@features/auth'
import type { AuthContextType, User } from '@features/auth'
import * as metricsApi from '@features/metrics/api/metricsApi'
import type { MetricsSummary } from '@features/metrics'

// Мокаем metrics API
vi.mock('@features/metrics/api/metricsApi', () => ({
  getSummary: vi.fn(),
}))

// Мокаем @ant-design/charts
vi.mock('@ant-design/charts', () => ({
  Tiny: {
    Area: ({ data }: { data: number[] }) => (
      <div data-testid="mock-sparkline">Sparkline with {data.length} points</div>
    ),
  },
}))

const mockGetSummary = metricsApi.getSummary as ReturnType<typeof vi.fn>

// Тестовые данные
const mockUser: User = {
  userId: 'user-1',
  username: 'testuser',
  role: 'admin',
}

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

// Мок AuthContext
const mockAuthContext: AuthContextType = {
  user: mockUser,
  isAuthenticated: true,
  isLoading: false,
  error: null,
  login: vi.fn(),
  logout: vi.fn(),
  clearError: vi.fn(),
}

// Wrapper с QueryClient и AuthContext
function createWrapper(authContext: AuthContextType = mockAuthContext) {
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

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetSummary.mockResolvedValue(mockSummary)
  })

  it('отображает MetricsWidget на dashboard (AC1, Story 6.5)', async () => {
    render(<DashboardPage />, { wrapper: createWrapper() })

    // Ждём загрузку метрик
    await waitFor(() => {
      expect(screen.getByTestId('metrics-widget')).toBeInTheDocument()
    })

    // Проверяем что карточки метрик отображаются
    expect(screen.getByTestId('metrics-card-rps')).toBeInTheDocument()
    expect(screen.getByTestId('metrics-card-latency')).toBeInTheDocument()
    expect(screen.getByTestId('metrics-card-error-rate')).toBeInTheDocument()
    expect(screen.getByTestId('metrics-card-active-routes')).toBeInTheDocument()
  })

  it('отображает приветствие с username', async () => {
    render(<DashboardPage />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByText(/testuser/)).toBeInTheDocument()
    })

    expect(screen.getByText('Dashboard')).toBeInTheDocument()
  })

  it('отображает роль пользователя', async () => {
    render(<DashboardPage />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByText(/Admin/)).toBeInTheDocument()
    })
  })

  it('отображает кнопку Logout', async () => {
    render(<DashboardPage />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByTestId('logout-button')).toBeInTheDocument()
    })
  })

  it('отображает MetricsWidget для developer роли (AC6)', async () => {
    const developerAuth: AuthContextType = {
      ...mockAuthContext,
      user: { ...mockUser, role: 'developer' },
    }

    render(<DashboardPage />, { wrapper: createWrapper(developerAuth) })

    // MetricsWidget виден для developer (read-only через backend filtering)
    await waitFor(() => {
      expect(screen.getByTestId('metrics-widget')).toBeInTheDocument()
    })

    // Роль отображается как Developer
    expect(screen.getByText(/Developer/)).toBeInTheDocument()
  })

  it('показывает loading state MetricsWidget', () => {
    // Не резолвим промис — виджет останется в loading
    mockGetSummary.mockImplementation(() => new Promise(() => {}))

    render(<DashboardPage />, { wrapper: createWrapper() })

    expect(screen.getByTestId('metrics-loading')).toBeInTheDocument()
  })

  it('показывает error state MetricsWidget при ошибке API', async () => {
    mockGetSummary.mockRejectedValue(new Error('Network error'))

    render(<DashboardPage />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByTestId('metrics-error-alert')).toBeInTheDocument()
    })

    expect(screen.getByText('Metrics unavailable')).toBeInTheDocument()
  })
})
