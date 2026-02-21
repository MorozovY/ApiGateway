// Тесты для TestPage (Story 8.9)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import type { ReactNode } from 'react'
import { TestPage } from './TestPage'
import * as routesApi from '@features/routes/api/routesApi'
import type { PageResponse, RouteResponse } from '@features/routes/types/route.types'

// Мокаем API
vi.mock('@features/routes/api/routesApi', () => ({
  fetchRoutes: vi.fn(),
}))

const mockFetchRoutes = routesApi.fetchRoutes as ReturnType<typeof vi.fn>

// Тестовые данные
const mockRoutes: PageResponse<RouteResponse> = {
  content: [
    {
      id: 'route-1',
      name: 'Orders API',
      path: '/api/orders',
      upstreamUrl: 'http://orders-service:8080',
      status: 'published',
      createdAt: '2026-02-20T10:00:00Z',
      updatedAt: '2026-02-20T10:00:00Z',
      createdBy: 'admin',
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
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

describe('TestPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockFetchRoutes.mockResolvedValue(mockRoutes)
  })

  it('отображает заголовок страницы', async () => {
    render(<TestPage />, { wrapper: createWrapper() })

    expect(screen.getByText('Test Load Generator')).toBeInTheDocument()
    expect(screen.getByTestId('test-page')).toBeInTheDocument()
  })

  it('отображает форму генератора нагрузки', async () => {
    render(<TestPage />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByTestId('load-generator-form')).toBeInTheDocument()
    })
  })

  it('не отображает progress и summary изначально', async () => {
    render(<TestPage />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByTestId('load-generator-form')).toBeInTheDocument()
    })

    // Progress не должен отображаться в idle состоянии
    expect(screen.queryByTestId('load-generator-progress')).not.toBeInTheDocument()
    // Summary тоже не должен отображаться
    expect(screen.queryByTestId('load-generator-summary')).not.toBeInTheDocument()
  })

  it('отображает контролы формы после загрузки маршрутов', async () => {
    render(<TestPage />, { wrapper: createWrapper() })

    await waitFor(() => {
      expect(screen.getByTestId('route-selector')).toBeInTheDocument()
    })

    expect(screen.getByTestId('rps-input')).toBeInTheDocument()
    expect(screen.getByTestId('duration-mode')).toBeInTheDocument()
    expect(screen.getByTestId('start-button')).toBeInTheDocument()
  })
})
