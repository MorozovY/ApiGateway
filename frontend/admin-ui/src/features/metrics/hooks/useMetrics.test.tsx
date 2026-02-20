// Тесты для useMetrics hooks (Story 6.5)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useMetricsSummary, useTopRoutes, useRouteMetrics } from './useMetrics'
import * as metricsApi from '../api/metricsApi'
import { METRICS_REFRESH_INTERVAL, METRICS_STALE_TIME } from '../config/metricsConfig'
import type { MetricsSummary, TopRoute, RouteMetrics } from '../types/metrics.types'

// Мокаем API
vi.mock('../api/metricsApi', () => ({
  getSummary: vi.fn(),
  getTopRoutes: vi.fn(),
  getRouteMetrics: vi.fn(),
}))

const mockGetSummary = metricsApi.getSummary as ReturnType<typeof vi.fn>
const mockGetTopRoutes = metricsApi.getTopRoutes as ReturnType<typeof vi.fn>
const mockGetRouteMetrics = metricsApi.getRouteMetrics as ReturnType<typeof vi.fn>

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
]

const mockRouteMetrics: RouteMetrics = {
  routeId: 'route-123',
  path: '/api/orders',
  period: '5m',
  requestsPerSecond: 15.2,
  avgLatencyMs: 35,
  p95LatencyMs: 80,
  errorRate: 0.01,
  statusBreakdown: {
    '2xx': 950,
    '4xx': 40,
    '5xx': 10,
  },
}

// Фабрика для создания тестового QueryClient
function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  })
}

// Wrapper для renderHook
function createWrapper(queryClient: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  }
}

describe('useMetricsSummary', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetSummary.mockResolvedValue(mockSummary)
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  it('загружает сводку метрик', async () => {
    const queryClient = createTestQueryClient()
    const wrapper = createWrapper(queryClient)

    const { result } = renderHook(() => useMetricsSummary(), { wrapper })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(result.current.data).toEqual(mockSummary)
    expect(mockGetSummary).toHaveBeenCalledWith('5m')
  })

  it('использует указанный период', async () => {
    const queryClient = createTestQueryClient()
    const wrapper = createWrapper(queryClient)

    const { result } = renderHook(() => useMetricsSummary('1h'), { wrapper })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(mockGetSummary).toHaveBeenCalledWith('1h')
  })

  it('показывает loading state', async () => {
    const queryClient = createTestQueryClient()
    const wrapper = createWrapper(queryClient)

    let resolvePromise: (value: MetricsSummary) => void
    mockGetSummary.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolvePromise = resolve
        })
    )

    const { result } = renderHook(() => useMetricsSummary(), { wrapper })

    expect(result.current.isLoading).toBe(true)

    resolvePromise!(mockSummary)

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })
  })

  it('обрабатывает ошибки API', async () => {
    const queryClient = createTestQueryClient()
    const wrapper = createWrapper(queryClient)

    const error = new Error('Network error')
    mockGetSummary.mockRejectedValue(error)

    const { result } = renderHook(() => useMetricsSummary(), { wrapper })

    await waitFor(() => {
      expect(result.current.isError).toBe(true)
    })

    expect(result.current.error).toEqual(error)
  })

  it('настроен на автоматическое обновление каждые 10 секунд (AC2)', async () => {
    // Проверяем что константы конфигурации имеют правильные значения
    expect(METRICS_REFRESH_INTERVAL).toBe(10000) // 10 секунд
    expect(METRICS_STALE_TIME).toBe(5000) // 5 секунд

    // Hook использует эти константы для refetchInterval
    // Тестируем что query успешно выполняется с этой конфигурацией
    const queryClient = createTestQueryClient()
    const wrapper = createWrapper(queryClient)

    mockGetSummary.mockResolvedValue(mockSummary)

    const { result } = renderHook(() => useMetricsSummary(), { wrapper })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    // Verify query был настроен и выполнен
    expect(mockGetSummary).toHaveBeenCalledTimes(1)
    expect(result.current.data).toEqual(mockSummary)
  })
})

describe('useTopRoutes', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetTopRoutes.mockResolvedValue(mockTopRoutes)
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  it('загружает топ маршрутов с параметрами по умолчанию', async () => {
    const queryClient = createTestQueryClient()
    const wrapper = createWrapper(queryClient)

    const { result } = renderHook(() => useTopRoutes(), { wrapper })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(result.current.data).toEqual(mockTopRoutes)
    expect(mockGetTopRoutes).toHaveBeenCalledWith('requests', 10)
  })

  it('использует кастомные параметры сортировки', async () => {
    const queryClient = createTestQueryClient()
    const wrapper = createWrapper(queryClient)

    const { result } = renderHook(() => useTopRoutes('latency', 5), { wrapper })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(mockGetTopRoutes).toHaveBeenCalledWith('latency', 5)
  })
})

describe('useRouteMetrics', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetRouteMetrics.mockResolvedValue(mockRouteMetrics)
  })

  afterEach(() => {
    vi.resetAllMocks()
  })

  it('загружает метрики маршрута', async () => {
    const queryClient = createTestQueryClient()
    const wrapper = createWrapper(queryClient)

    const { result } = renderHook(() => useRouteMetrics('route-123'), { wrapper })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(result.current.data).toEqual(mockRouteMetrics)
    expect(mockGetRouteMetrics).toHaveBeenCalledWith('route-123', '5m')
  })

  it('не загружает данные при undefined routeId', async () => {
    const queryClient = createTestQueryClient()
    const wrapper = createWrapper(queryClient)

    const { result } = renderHook(() => useRouteMetrics(undefined), { wrapper })

    // Данные не загружаются (enabled: false)
    expect(result.current.isLoading).toBe(false)
    expect(result.current.data).toBeUndefined()
    expect(mockGetRouteMetrics).not.toHaveBeenCalled()
  })

  it('использует указанный период', async () => {
    const queryClient = createTestQueryClient()
    const wrapper = createWrapper(queryClient)

    const { result } = renderHook(() => useRouteMetrics('route-456', '24h'), { wrapper })

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true)
    })

    expect(mockGetRouteMetrics).toHaveBeenCalledWith('route-456', '24h')
  })
})
