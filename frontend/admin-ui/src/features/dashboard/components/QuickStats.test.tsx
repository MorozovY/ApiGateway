/**
 * Тесты для QuickStats компонента (Story 16.2)
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { QuickStats } from './QuickStats'
import * as dashboardHooks from '../hooks/useDashboard'
import type { DashboardSummary } from '../types/dashboard.types'

// Мокаем useNavigate
const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

// Мокаем хуки Dashboard
vi.mock('../hooks/useDashboard')

const mockSummary: DashboardSummary = {
  routesByStatus: {
    draft: 3,
    pending: 2,
    published: 10,
    rejected: 1,
  },
  pendingApprovalsCount: 2,
}

describe('QuickStats', () => {
  let queryClient: QueryClient

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    })
    vi.clearAllMocks()
  })

  const renderComponent = () => {
    return render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <QuickStats />
        </MemoryRouter>
      </QueryClientProvider>,
    )
  }

  it('отображает статистику маршрутов по статусам', async () => {
    vi.mocked(dashboardHooks.useDashboardSummary).mockReturnValue({
      data: mockSummary,
      isLoading: false,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as ReturnType<typeof dashboardHooks.useDashboardSummary>)

    renderComponent()

    expect(screen.getByText('Черновики')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()

    expect(screen.getByText('На согласовании')).toBeInTheDocument()
    expect(screen.getByText('2')).toBeInTheDocument()

    expect(screen.getByText('Опубликованы')).toBeInTheDocument()
    expect(screen.getByText('10')).toBeInTheDocument()

    expect(screen.getByText('Отклонены')).toBeInTheDocument()
    expect(screen.getByText('1')).toBeInTheDocument()
  })

  it('показывает спиннеры при загрузке', () => {
    vi.mocked(dashboardHooks.useDashboardSummary).mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as unknown as ReturnType<typeof dashboardHooks.useDashboardSummary>)

    renderComponent()

    // При загрузке не должно быть статистики
    expect(screen.queryByText('Черновики')).not.toBeInTheDocument()
  })

  it('показывает ошибку при неудачной загрузке', () => {
    vi.mocked(dashboardHooks.useDashboardSummary).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: new Error('Ошибка сети'),
      refresh: vi.fn(),
    } as unknown as ReturnType<typeof dashboardHooks.useDashboardSummary>)

    renderComponent()

    expect(screen.getByText('Ошибка загрузки статистики')).toBeInTheDocument()
    expect(screen.getByText('Повторить')).toBeInTheDocument()
  })

  it('переходит на страницу маршрутов с фильтром при клике на карточку', async () => {
    vi.mocked(dashboardHooks.useDashboardSummary).mockReturnValue({
      data: mockSummary,
      isLoading: false,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as ReturnType<typeof dashboardHooks.useDashboardSummary>)

    renderComponent()

    const user = userEvent.setup()

    // Клик на карточку "Черновики"
    const draftCard = screen.getByText('Черновики').closest('.ant-card')
    await user.click(draftCard!)

    expect(mockNavigate).toHaveBeenCalledWith('/routes?status=draft')
  })

  it('вызывает refresh при клике на повторить', async () => {
    const mockRefresh = vi.fn()
    vi.mocked(dashboardHooks.useDashboardSummary).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: new Error('Test'),
      refresh: mockRefresh,
    } as unknown as ReturnType<typeof dashboardHooks.useDashboardSummary>)

    renderComponent()

    const user = userEvent.setup()
    await user.click(screen.getByText('Повторить'))

    expect(mockRefresh).toHaveBeenCalled()
  })
})
