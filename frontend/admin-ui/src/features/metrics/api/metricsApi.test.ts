// Тесты для metricsApi (Story 6.5)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import axios from '@/shared/utils/axios'
import { getSummary, getRouteMetrics, getTopRoutes } from './metricsApi'
import type { MetricsSummary, RouteMetrics, TopRoute } from '../types/metrics.types'

// Мокаем axios
vi.mock('@/shared/utils/axios', () => ({
  default: {
    get: vi.fn(),
  },
}))

const mockAxiosGet = axios.get as ReturnType<typeof vi.fn>

describe('metricsApi', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('getSummary', () => {
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

    it('получает сводку метрик с периодом по умолчанию', async () => {
      mockAxiosGet.mockResolvedValue({ data: mockSummary })

      const result = await getSummary()

      expect(mockAxiosGet).toHaveBeenCalledWith('/api/v1/metrics/summary', {
        params: { period: '5m' },
      })
      expect(result).toEqual(mockSummary)
    })

    it('получает сводку метрик с указанным периодом', async () => {
      mockAxiosGet.mockResolvedValue({ data: mockSummary })

      await getSummary('1h')

      expect(mockAxiosGet).toHaveBeenCalledWith('/api/v1/metrics/summary', {
        params: { period: '1h' },
      })
    })

    it('пробрасывает ошибку API', async () => {
      const error = new Error('Network error')
      mockAxiosGet.mockRejectedValue(error)

      await expect(getSummary()).rejects.toThrow('Network error')
    })
  })

  describe('getRouteMetrics', () => {
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

    it('получает метрики конкретного маршрута', async () => {
      mockAxiosGet.mockResolvedValue({ data: mockRouteMetrics })

      const result = await getRouteMetrics('route-123')

      expect(mockAxiosGet).toHaveBeenCalledWith('/api/v1/metrics/routes/route-123', {
        params: { period: '5m' },
      })
      expect(result).toEqual(mockRouteMetrics)
    })

    it('получает метрики маршрута с указанным периодом', async () => {
      mockAxiosGet.mockResolvedValue({ data: mockRouteMetrics })

      await getRouteMetrics('route-456', '24h')

      expect(mockAxiosGet).toHaveBeenCalledWith('/api/v1/metrics/routes/route-456', {
        params: { period: '24h' },
      })
    })
  })

  describe('getTopRoutes', () => {
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

    it('получает топ маршрутов с параметрами по умолчанию', async () => {
      mockAxiosGet.mockResolvedValue({ data: mockTopRoutes })

      const result = await getTopRoutes()

      expect(mockAxiosGet).toHaveBeenCalledWith('/api/v1/metrics/top-routes', {
        params: { by: 'requests', limit: 10 },
      })
      expect(result).toEqual(mockTopRoutes)
    })

    it('получает топ маршрутов с кастомной сортировкой', async () => {
      mockAxiosGet.mockResolvedValue({ data: mockTopRoutes })

      await getTopRoutes('latency', 5)

      expect(mockAxiosGet).toHaveBeenCalledWith('/api/v1/metrics/top-routes', {
        params: { by: 'latency', limit: 5 },
      })
    })

    it('получает топ маршрутов по ошибкам', async () => {
      mockAxiosGet.mockResolvedValue({ data: mockTopRoutes })

      await getTopRoutes('errors', 20)

      expect(mockAxiosGet).toHaveBeenCalledWith('/api/v1/metrics/top-routes', {
        params: { by: 'errors', limit: 20 },
      })
    })
  })
})
