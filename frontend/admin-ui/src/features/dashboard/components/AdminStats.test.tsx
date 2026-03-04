/**
 * Тесты для AdminStats компонента (Story 16.2, AC3)
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { AdminStats } from './AdminStats'
import { AuthContext } from '@features/auth'
import type { AuthContextType, User } from '@features/auth'
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

// Хелпер для создания auth context
function createAuthContext(role: string): AuthContextType {
  const user: User = {
    userId: 'user-1',
    username: 'testuser',
    role,
  }
  return {
    user,
    isAuthenticated: true,
    isLoading: false,
    error: null,
    login: vi.fn(),
    logout: vi.fn(),
    clearError: vi.fn(),
  }
}

const mockAdminSummary: DashboardSummary = {
  routesByStatus: {
    draft: 5,
    pending: 3,
    published: 10,
    rejected: 2,
  },
  pendingApprovalsCount: 3,
  totalUsers: 15,
  totalConsumers: 8,
  systemHealth: 'healthy',
}

describe('AdminStats', () => {
  let queryClient: QueryClient

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
      },
    })
    vi.clearAllMocks()
  })

  const renderComponent = (authContext: AuthContextType) => {
    return render(
      <QueryClientProvider client={queryClient}>
        <AuthContext.Provider value={authContext}>
          <MemoryRouter>
            <AdminStats />
          </MemoryRouter>
        </AuthContext.Provider>
      </QueryClientProvider>,
    )
  }

  it('отображает статистику для admin', () => {
    vi.mocked(dashboardHooks.useDashboardSummary).mockReturnValue({
      data: mockAdminSummary,
      isLoading: false,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as ReturnType<typeof dashboardHooks.useDashboardSummary>)

    renderComponent(createAuthContext('admin'))

    expect(screen.getByText('Пользователей')).toBeInTheDocument()
    expect(screen.getByText('15')).toBeInTheDocument()
    expect(screen.getByText('API Consumers')).toBeInTheDocument()
    expect(screen.getByText('8')).toBeInTheDocument()
  })

  it('отображает статус системы healthy', () => {
    vi.mocked(dashboardHooks.useDashboardSummary).mockReturnValue({
      data: mockAdminSummary,
      isLoading: false,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as ReturnType<typeof dashboardHooks.useDashboardSummary>)

    renderComponent(createAuthContext('admin'))

    expect(screen.getByText('Статус системы')).toBeInTheDocument()
    expect(screen.getByText('Все сервисы работают')).toBeInTheDocument()
  })

  it('отображает статус системы degraded', () => {
    vi.mocked(dashboardHooks.useDashboardSummary).mockReturnValue({
      data: { ...mockAdminSummary, systemHealth: 'degraded' },
      isLoading: false,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as ReturnType<typeof dashboardHooks.useDashboardSummary>)

    renderComponent(createAuthContext('admin'))

    expect(screen.getByText('Частичные проблемы')).toBeInTheDocument()
  })

  it('не отображается для developer', () => {
    vi.mocked(dashboardHooks.useDashboardSummary).mockReturnValue({
      data: mockAdminSummary,
      isLoading: false,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as ReturnType<typeof dashboardHooks.useDashboardSummary>)

    renderComponent(createAuthContext('developer'))

    expect(screen.queryByText('Пользователей')).not.toBeInTheDocument()
  })

  it('не отображается для security', () => {
    vi.mocked(dashboardHooks.useDashboardSummary).mockReturnValue({
      data: mockAdminSummary,
      isLoading: false,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as ReturnType<typeof dashboardHooks.useDashboardSummary>)

    renderComponent(createAuthContext('security'))

    expect(screen.queryByText('Пользователей')).not.toBeInTheDocument()
  })

  it('не отображается при загрузке', () => {
    vi.mocked(dashboardHooks.useDashboardSummary).mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as unknown as ReturnType<typeof dashboardHooks.useDashboardSummary>)

    renderComponent(createAuthContext('admin'))

    expect(screen.queryByText('Пользователей')).not.toBeInTheDocument()
  })

  it('переходит на /users при клике на карточку пользователей', async () => {
    vi.mocked(dashboardHooks.useDashboardSummary).mockReturnValue({
      data: mockAdminSummary,
      isLoading: false,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as ReturnType<typeof dashboardHooks.useDashboardSummary>)

    renderComponent(createAuthContext('admin'))

    const user = userEvent.setup()
    const usersCard = screen.getByText('Пользователей').closest('.ant-card')
    await user.click(usersCard!)

    expect(mockNavigate).toHaveBeenCalledWith('/users')
  })

  it('переходит на /consumers при клике на карточку consumers', async () => {
    vi.mocked(dashboardHooks.useDashboardSummary).mockReturnValue({
      data: mockAdminSummary,
      isLoading: false,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as ReturnType<typeof dashboardHooks.useDashboardSummary>)

    renderComponent(createAuthContext('admin'))

    const user = userEvent.setup()
    const consumersCard = screen.getByText('API Consumers').closest('.ant-card')
    await user.click(consumersCard!)

    expect(mockNavigate).toHaveBeenCalledWith('/consumers')
  })

  it('переходит на /metrics при клике на карточку статуса', async () => {
    vi.mocked(dashboardHooks.useDashboardSummary).mockReturnValue({
      data: mockAdminSummary,
      isLoading: false,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as ReturnType<typeof dashboardHooks.useDashboardSummary>)

    renderComponent(createAuthContext('admin'))

    const user = userEvent.setup()
    const healthCard = screen.getByText('Статус системы').closest('.ant-card')
    await user.click(healthCard!)

    expect(mockNavigate).toHaveBeenCalledWith('/metrics')
  })

  it('показывает прочерк когда totalConsumers равен null', () => {
    vi.mocked(dashboardHooks.useDashboardSummary).mockReturnValue({
      data: { ...mockAdminSummary, totalConsumers: null },
      isLoading: false,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as ReturnType<typeof dashboardHooks.useDashboardSummary>)

    renderComponent(createAuthContext('admin'))

    expect(screen.getByText('—')).toBeInTheDocument()
  })
})
