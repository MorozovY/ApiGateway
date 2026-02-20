// Тесты для MetricsPage (Story 6.5)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import type { ReactNode } from 'react'
import MetricsPage from './MetricsPage'
import * as metricsApi from '../api/metricsApi'
import type { MetricsSummary, TopRoute } from '../types/metrics.types'

// Мокаем API
vi.mock('../api/metricsApi', () => ({
  getSummary: vi.fn(),
  getTopRoutes: vi.fn(),
}))

const mockGetSummary = metricsApi.getSummary as ReturnType<typeof vi.fn>
const mockGetTopRoutes = metricsApi.getTopRoutes as ReturnType<typeof vi.fn>

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
    requestsPerSecond: 15.2,
    avgLatencyMs: 35,
    errorRate: 0.01,
  },
  {
    routeId: 'route-2',
    path: '/api/users',
    requestsPerSecond: 10.5,
    avgLatencyMs: 28,
    errorRate: 0.005,
  },
]

// Wrapper для тестов
function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>{children}</BrowserRouter>
      </QueryClientProvider>
    )
  }
}

describe('MetricsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetSummary.mockResolvedValue(mockSummary)
    mockGetTopRoutes.mockResolvedValue(mockTopRoutes)
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
      'http://localhost:3001/d/gateway-dashboard/api-gateway'
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
})
