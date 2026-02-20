// Тесты для MetricsWidget (Story 6.5)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor, fireEvent, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import type { ReactNode } from 'react'
import MetricsWidget from './MetricsWidget'
import * as metricsApi from '../api/metricsApi'
import type { MetricsSummary } from '../types/metrics.types'

// Мокаем API
vi.mock('../api/metricsApi', () => ({
  getSummary: vi.fn(),
}))

// Мокаем @ant-design/charts (AC2 sparkline charts)
vi.mock('@ant-design/charts', () => ({
  Tiny: {
    Area: ({ data, 'data-testid': testId }: { data: number[]; 'data-testid'?: string }) => (
      <div data-testid={testId || 'tiny-area-chart'} data-points={data.length}>
        Mock TinyArea Chart with {data.length} points
      </div>
    ),
  },
}))

// Мокаем useNavigate
const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

const mockGetSummary = metricsApi.getSummary as ReturnType<typeof vi.fn>

// Тестовые данные
const mockSummaryHealthy: MetricsSummary = {
  period: '5m',
  totalRequests: 12500,
  requestsPerSecond: 41.7,
  avgLatencyMs: 45,
  p95LatencyMs: 120,
  p99LatencyMs: 250,
  errorRate: 0.005, // < 1% — зелёный
  errorCount: 62,
  activeRoutes: 45,
}

const mockSummaryWarning: MetricsSummary = {
  ...mockSummaryHealthy,
  errorRate: 0.03, // 1-5% — жёлтый
}

const mockSummaryCritical: MetricsSummary = {
  ...mockSummaryHealthy,
  errorRate: 0.08, // > 5% — красный
}

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

describe('MetricsWidget', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockNavigate.mockClear()
  })

  it('отображает loading состояние', async () => {
    let resolvePromise: (value: MetricsSummary) => void
    mockGetSummary.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolvePromise = resolve
        })
    )

    render(<MetricsWidget />, { wrapper: createWrapper() })

    expect(screen.getByTestId('metrics-loading')).toBeInTheDocument()
    expect(screen.getByText('Loading metrics...')).toBeInTheDocument()

    // Резолвим чтобы тест не зависал
    resolvePromise!(mockSummaryHealthy)
  })

  it('отображает метрики после загрузки', async () => {
    mockGetSummary.mockResolvedValue(mockSummaryHealthy)

    render(<MetricsWidget />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByTestId('metrics-widget')).toBeInTheDocument()
    })

    // Проверяем что все карточки отрендерены
    expect(screen.getByTestId('metrics-card-rps')).toBeInTheDocument()
    expect(screen.getByTestId('metrics-card-latency')).toBeInTheDocument()
    expect(screen.getByTestId('metrics-card-error-rate')).toBeInTheDocument()
    expect(screen.getByTestId('metrics-card-active-routes')).toBeInTheDocument()

    // Проверяем заголовки карточек
    expect(screen.getByText('Requests per Second')).toBeInTheDocument()
    expect(screen.getByText('Avg Latency')).toBeInTheDocument()
    expect(screen.getByText('Error Rate')).toBeInTheDocument()
    expect(screen.getByText('Active Routes')).toBeInTheDocument()
  })

  it('отображает error состояние при ошибке API', async () => {
    mockGetSummary.mockRejectedValue(new Error('Network error'))

    render(<MetricsWidget />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByTestId('metrics-error-alert')).toBeInTheDocument()
    })

    expect(screen.getByText('Metrics unavailable')).toBeInTheDocument()
    expect(screen.getByTestId('metrics-retry-button')).toBeInTheDocument()
  })

  it('retry кнопка перезагружает данные', async () => {
    mockGetSummary.mockRejectedValueOnce(new Error('Network error'))

    render(<MetricsWidget />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByTestId('metrics-error-alert')).toBeInTheDocument()
    })

    // Теперь API вернёт успешный результат
    mockGetSummary.mockResolvedValue(mockSummaryHealthy)

    fireEvent.click(screen.getByTestId('metrics-retry-button'))

    await waitFor(() => {
      expect(screen.getByTestId('metrics-widget')).toBeInTheDocument()
    })
  })

  it('использует зелёный цвет для error rate < 1%', async () => {
    mockGetSummary.mockResolvedValue(mockSummaryHealthy)

    render(<MetricsWidget />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByTestId('metrics-widget')).toBeInTheDocument()
    })

    const errorRateCard = screen.getByTestId('metrics-card-error-rate')
    expect(errorRateCard).toHaveAttribute('data-error-status', 'healthy')
  })

  it('использует жёлтый цвет для error rate 1-5%', async () => {
    mockGetSummary.mockResolvedValue(mockSummaryWarning)

    render(<MetricsWidget />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByTestId('metrics-widget')).toBeInTheDocument()
    })

    const errorRateCard = screen.getByTestId('metrics-card-error-rate')
    expect(errorRateCard).toHaveAttribute('data-error-status', 'warning')
  })

  it('использует красный цвет для error rate > 5%', async () => {
    mockGetSummary.mockResolvedValue(mockSummaryCritical)

    render(<MetricsWidget />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByTestId('metrics-widget')).toBeInTheDocument()
    })

    const errorRateCard = screen.getByTestId('metrics-card-error-rate')
    expect(errorRateCard).toHaveAttribute('data-error-status', 'critical')
  })

  it('навигирует на /metrics при клике на карточку', async () => {
    mockGetSummary.mockResolvedValue(mockSummaryHealthy)

    render(<MetricsWidget />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByTestId('metrics-widget')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByTestId('metrics-card-rps'))

    expect(mockNavigate).toHaveBeenCalledWith('/metrics')
  })

  it('вызывает кастомный onClick обработчик', async () => {
    mockGetSummary.mockResolvedValue(mockSummaryHealthy)
    const customOnClick = vi.fn()

    render(<MetricsWidget onClick={customOnClick} />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByTestId('metrics-widget')).toBeInTheDocument()
    })

    fireEvent.click(screen.getByTestId('metrics-card-rps'))

    expect(customOnClick).toHaveBeenCalled()
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  // Тесты для sparkline (AC2)
  describe('Sparkline charts (AC2)', () => {
    it('отображает sparkline placeholders при первой загрузке', async () => {
      mockGetSummary.mockResolvedValue(mockSummaryHealthy)

      render(<MetricsWidget />, { wrapper: createWrapper() })

      await waitFor(() => {
        expect(screen.getByTestId('metrics-widget')).toBeInTheDocument()
      })

      // При первой загрузке недостаточно точек для графика
      expect(screen.getByTestId('sparkline-rps')).toBeInTheDocument()
      expect(screen.getByTestId('sparkline-latency')).toBeInTheDocument()
      // Должны отображать "Collecting data..." пока точек меньше MIN_SPARKLINE_POINTS
      expect(screen.getAllByText('Collecting data...').length).toBeGreaterThanOrEqual(1)
    })

    it('отображает sparkline графики после накопления данных', async () => {
      // Эмулируем несколько обновлений данных
      let callCount = 0
      mockGetSummary.mockImplementation(() => {
        callCount++
        return Promise.resolve({
          ...mockSummaryHealthy,
          requestsPerSecond: 40 + callCount,
          avgLatencyMs: 45 + callCount,
        })
      })

      const queryClient = new QueryClient({
        defaultOptions: {
          queries: { retry: false, refetchInterval: false },
        },
      })

      const { rerender } = render(
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            <MetricsWidget />
          </BrowserRouter>
        </QueryClientProvider>
      )

      await waitFor(() => {
        expect(screen.getByTestId('metrics-widget')).toBeInTheDocument()
      })

      // Симулируем несколько refetch для накопления истории
      await act(async () => {
        await queryClient.refetchQueries({ queryKey: ['metrics', 'summary'] })
      })
      await act(async () => {
        await queryClient.refetchQueries({ queryKey: ['metrics', 'summary'] })
      })
      await act(async () => {
        await queryClient.refetchQueries({ queryKey: ['metrics', 'summary'] })
      })

      // После нескольких обновлений sparkline должны отображаться
      rerender(
        <QueryClientProvider client={queryClient}>
          <BrowserRouter>
            <MetricsWidget />
          </BrowserRouter>
        </QueryClientProvider>
      )

      // Sparkline элементы присутствуют
      expect(screen.getByTestId('sparkline-rps')).toBeInTheDocument()
      expect(screen.getByTestId('sparkline-latency')).toBeInTheDocument()
    })
  })
})
