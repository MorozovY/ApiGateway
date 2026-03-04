/**
 * Тесты для RecentActivity компонента (Story 16.2)
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { RecentActivity } from './RecentActivity'
import * as dashboardHooks from '../hooks/useDashboard'
import type { RecentActivityResponse } from '../types/dashboard.types'

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

const mockActivityResponse: RecentActivityResponse = {
  items: [
    {
      id: '1',
      action: 'created',
      entityType: 'route',
      entityId: 'route-1',
      entityName: '/api/users',
      performedBy: 'developer',
      performedAt: new Date(Date.now() - 5 * 60 * 1000).toISOString(), // 5 минут назад
    },
    {
      id: '2',
      action: 'approved',
      entityType: 'route',
      entityId: 'route-2',
      entityName: '/api/orders',
      performedBy: 'security',
      performedAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(), // 2 часа назад
    },
  ],
}

describe('RecentActivity', () => {
  let queryClient: QueryClient

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    })
    vi.clearAllMocks()
  })

  const renderComponent = (limit = 5) => {
    return render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter>
          <RecentActivity limit={limit} />
        </MemoryRouter>
      </QueryClientProvider>,
    )
  }

  it('отображает заголовок "Последние действия"', () => {
    vi.mocked(dashboardHooks.useRecentActivity).mockReturnValue({
      data: mockActivityResponse,
      isLoading: false,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as unknown as ReturnType<typeof dashboardHooks.useRecentActivity>)

    renderComponent()

    expect(screen.getByText('Последние действия')).toBeInTheDocument()
  })

  it('отображает список действий', () => {
    vi.mocked(dashboardHooks.useRecentActivity).mockReturnValue({
      data: mockActivityResponse,
      isLoading: false,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as unknown as ReturnType<typeof dashboardHooks.useRecentActivity>)

    renderComponent()

    expect(screen.getByText('/api/users')).toBeInTheDocument()
    expect(screen.getByText('/api/orders')).toBeInTheDocument()
  })

  it('отображает теги действий (Создан, Одобрен)', () => {
    vi.mocked(dashboardHooks.useRecentActivity).mockReturnValue({
      data: mockActivityResponse,
      isLoading: false,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as unknown as ReturnType<typeof dashboardHooks.useRecentActivity>)

    renderComponent()

    expect(screen.getByText('Создан')).toBeInTheDocument()
    expect(screen.getByText('Одобрен')).toBeInTheDocument()
  })

  it('отображает имена пользователей', () => {
    vi.mocked(dashboardHooks.useRecentActivity).mockReturnValue({
      data: mockActivityResponse,
      isLoading: false,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as unknown as ReturnType<typeof dashboardHooks.useRecentActivity>)

    renderComponent()

    expect(screen.getByText('developer')).toBeInTheDocument()
    expect(screen.getByText('security')).toBeInTheDocument()
  })

  it('показывает спиннер при загрузке', () => {
    vi.mocked(dashboardHooks.useRecentActivity).mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as unknown as ReturnType<typeof dashboardHooks.useRecentActivity>)

    renderComponent()

    // При загрузке не должно быть списка
    expect(screen.queryByText('/api/users')).not.toBeInTheDocument()
  })

  it('показывает ошибку при неудачной загрузке', () => {
    vi.mocked(dashboardHooks.useRecentActivity).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: new Error('Ошибка сети'),
      refresh: vi.fn(),
    } as unknown as ReturnType<typeof dashboardHooks.useRecentActivity>)

    renderComponent()

    expect(screen.getByText('Ошибка загрузки')).toBeInTheDocument()
    expect(screen.getByText('Повторить')).toBeInTheDocument()
  })

  it('показывает пустое состояние когда нет действий', () => {
    vi.mocked(dashboardHooks.useRecentActivity).mockReturnValue({
      data: { items: [] },
      isLoading: false,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as unknown as ReturnType<typeof dashboardHooks.useRecentActivity>)

    renderComponent()

    expect(screen.getByText('Нет действий для отображения')).toBeInTheDocument()
  })

  it('вызывает refresh при клике на кнопку повторить', async () => {
    const mockRefresh = vi.fn()
    vi.mocked(dashboardHooks.useRecentActivity).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: new Error('Test'),
      refresh: mockRefresh,
    } as unknown as ReturnType<typeof dashboardHooks.useRecentActivity>)

    renderComponent()

    const user = userEvent.setup()
    await user.click(screen.getByText('Повторить'))

    expect(mockRefresh).toHaveBeenCalled()
  })

  it('переходит к маршруту при клике на элемент', async () => {
    vi.mocked(dashboardHooks.useRecentActivity).mockReturnValue({
      data: mockActivityResponse,
      isLoading: false,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as unknown as ReturnType<typeof dashboardHooks.useRecentActivity>)

    renderComponent()

    const user = userEvent.setup()
    await user.click(screen.getByText('/api/users'))

    expect(mockNavigate).toHaveBeenCalledWith('/routes/route-1')
  })
})
