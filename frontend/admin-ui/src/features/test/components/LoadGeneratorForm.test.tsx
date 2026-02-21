// Тесты для LoadGeneratorForm (Story 8.9, AC2)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import type { ReactNode } from 'react'
import { LoadGeneratorForm } from './LoadGeneratorForm'
import * as routesApi from '@features/routes/api/routesApi'
import type { RouteListResponse } from '@features/routes/types/route.types'

// Мокаем API
vi.mock('@features/routes/api/routesApi', () => ({
  fetchRoutes: vi.fn(),
}))

const mockFetchRoutes = routesApi.fetchRoutes as ReturnType<typeof vi.fn>

// Тестовые данные
const mockRoutes: RouteListResponse = {
  items: [
    {
      id: 'route-1',
      name: 'Orders API',
      path: '/api/orders',
      upstreamUrl: 'http://orders-service:8080',
      methods: ['GET'],
      description: null,
      status: 'published',
      createdAt: '2026-02-20T10:00:00Z',
      updatedAt: '2026-02-20T10:00:00Z',
      createdBy: 'admin',
      rateLimitId: null,
    },
    {
      id: 'route-2',
      name: 'Users API',
      path: '/api/users',
      upstreamUrl: 'http://users-service:8080',
      methods: ['GET'],
      description: null,
      status: 'published',
      createdAt: '2026-02-20T10:00:00Z',
      updatedAt: '2026-02-20T10:00:00Z',
      createdBy: 'admin',
      rateLimitId: null,
    },
  ],
  total: 2,
  offset: 0,
  limit: 20,
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

describe('LoadGeneratorForm', () => {
  const mockOnStart = vi.fn()
  const mockOnStop = vi.fn()

  beforeEach(() => {
    vi.clearAllMocks()
    mockFetchRoutes.mockResolvedValue(mockRoutes)
  })

  it('отображает форму с контролами', async () => {
    render(
      <LoadGeneratorForm isRunning={false} onStart={mockOnStart} onStop={mockOnStop} />,
      { wrapper: createWrapper() }
    )

    // Ждём загрузки маршрутов и появления контролов
    await waitFor(() => {
      expect(screen.getByTestId('route-selector')).toBeInTheDocument()
    })

    // Проверяем наличие всех контролов
    expect(screen.getByTestId('load-generator-form')).toBeInTheDocument()
    expect(screen.getByTestId('rps-input')).toBeInTheDocument()
    expect(screen.getByTestId('duration-mode')).toBeInTheDocument()
    expect(screen.getByTestId('start-button')).toBeInTheDocument()
  })

  it('отображает loading при загрузке маршрутов', () => {
    let resolvePromise: (value: PageResponse<RouteResponse>) => void
    mockFetchRoutes.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolvePromise = resolve
        })
    )

    render(
      <LoadGeneratorForm isRunning={false} onStart={mockOnStart} onStop={mockOnStop} />,
      { wrapper: createWrapper() }
    )

    expect(screen.getByText('Loading routes...')).toBeInTheDocument()

    // Резолвим чтобы тест не зависал
    resolvePromise!(mockRoutes)
  })

  it('отображает предупреждение когда нет опубликованных маршрутов', async () => {
    mockFetchRoutes.mockResolvedValue({ ...mockRoutes, items: [] })

    render(
      <LoadGeneratorForm isRunning={false} onStart={mockOnStart} onStop={mockOnStop} />,
      { wrapper: createWrapper() }
    )

    await waitFor(() => {
      expect(screen.getByTestId('no-routes-alert')).toBeInTheDocument()
    })

    expect(screen.getByText('No published routes')).toBeInTheDocument()
  })

  it('кнопка Start disabled пока не выбран маршрут', async () => {
    render(
      <LoadGeneratorForm isRunning={false} onStart={mockOnStart} onStop={mockOnStop} />,
      { wrapper: createWrapper() }
    )

    await waitFor(() => {
      expect(screen.getByTestId('start-button')).toBeInTheDocument()
    })

    const startButton = screen.getByTestId('start-button')
    expect(startButton).toBeDisabled()
  })

  it('вызывает onStart с правильной конфигурацией', async () => {
    render(
      <LoadGeneratorForm isRunning={false} onStart={mockOnStart} onStop={mockOnStop} />,
      { wrapper: createWrapper() }
    )

    await waitFor(() => {
      expect(screen.getByTestId('route-selector')).toBeInTheDocument()
    })

    // Выбираем маршрут через Ant Design Select
    const routeSelector = screen.getByTestId('route-selector')
    const input = routeSelector.querySelector('.ant-select-selector')!
    fireEvent.mouseDown(input)

    await waitFor(() => {
      // Ищем первый опцию в dropdown
      const option = screen.getByText('/api/orders (Orders API)')
      fireEvent.click(option)
    })

    // Кликаем Start
    const startButton = screen.getByTestId('start-button')
    fireEvent.click(startButton)

    expect(mockOnStart).toHaveBeenCalledWith({
      routeId: 'route-1',
      routePath: '/api/orders',
      requestsPerSecond: 10,
      durationSeconds: null,
    })
  })

  it('показывает Stop кнопку когда isRunning=true', async () => {
    render(
      <LoadGeneratorForm isRunning={true} onStart={mockOnStart} onStop={mockOnStop} />,
      { wrapper: createWrapper() }
    )

    await waitFor(() => {
      expect(screen.getByTestId('stop-button')).toBeInTheDocument()
    })

    const stopButton = screen.getByTestId('stop-button')
    fireEvent.click(stopButton)

    expect(mockOnStop).toHaveBeenCalled()
  })

  it('блокирует контролы когда isRunning=true', async () => {
    render(
      <LoadGeneratorForm isRunning={true} onStart={mockOnStart} onStop={mockOnStop} />,
      { wrapper: createWrapper() }
    )

    await waitFor(() => {
      expect(screen.getByTestId('route-selector')).toBeInTheDocument()
    })

    // Проверяем что Select заблокирован
    const selector = screen.getByTestId('route-selector')
    expect(selector).toHaveClass('ant-select-disabled')
  })

  it('загружает только опубликованные маршруты', async () => {
    render(
      <LoadGeneratorForm isRunning={false} onStart={mockOnStart} onStop={mockOnStop} />,
      { wrapper: createWrapper() }
    )

    await waitFor(() => {
      expect(mockFetchRoutes).toHaveBeenCalled()
    })

    // Проверяем что API вызывается с фильтром status=published
    expect(mockFetchRoutes).toHaveBeenCalledWith(expect.objectContaining({ status: 'published' }))
  })
})
