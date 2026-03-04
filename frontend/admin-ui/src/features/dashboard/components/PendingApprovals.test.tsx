/**
 * Тесты для PendingApprovals компонента (Story 16.2, AC2)
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { PendingApprovals } from './PendingApprovals'
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

const mockSummary: DashboardSummary = {
  routesByStatus: {
    draft: 5,
    pending: 3,
    published: 10,
    rejected: 2,
  },
  pendingApprovalsCount: 5,
}

describe('PendingApprovals', () => {
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
            <PendingApprovals />
          </MemoryRouter>
        </AuthContext.Provider>
      </QueryClientProvider>,
    )
  }

  it('отображает уведомление для security когда есть pending маршруты', () => {
    vi.mocked(dashboardHooks.useDashboardSummary).mockReturnValue({
      data: mockSummary,
      isLoading: false,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as ReturnType<typeof dashboardHooks.useDashboardSummary>)

    renderComponent(createAuthContext('security'))

    expect(screen.getByText(/5 маршрутов ожидает согласования/)).toBeInTheDocument()
  })

  it('отображает уведомление для admin когда есть pending маршруты', () => {
    vi.mocked(dashboardHooks.useDashboardSummary).mockReturnValue({
      data: mockSummary,
      isLoading: false,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as ReturnType<typeof dashboardHooks.useDashboardSummary>)

    renderComponent(createAuthContext('admin'))

    expect(screen.getByText(/5 маршрутов ожидает согласования/)).toBeInTheDocument()
  })

  it('не отображается для developer', () => {
    vi.mocked(dashboardHooks.useDashboardSummary).mockReturnValue({
      data: mockSummary,
      isLoading: false,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as ReturnType<typeof dashboardHooks.useDashboardSummary>)

    renderComponent(createAuthContext('developer'))

    expect(screen.queryByText(/ожидает согласования/)).not.toBeInTheDocument()
  })

  it('не отображается когда нет pending маршрутов', () => {
    vi.mocked(dashboardHooks.useDashboardSummary).mockReturnValue({
      data: { ...mockSummary, pendingApprovalsCount: 0 },
      isLoading: false,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as ReturnType<typeof dashboardHooks.useDashboardSummary>)

    renderComponent(createAuthContext('security'))

    expect(screen.queryByText(/ожидает согласования/)).not.toBeInTheDocument()
  })

  it('не отображается при загрузке', () => {
    vi.mocked(dashboardHooks.useDashboardSummary).mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as unknown as ReturnType<typeof dashboardHooks.useDashboardSummary>)

    renderComponent(createAuthContext('security'))

    expect(screen.queryByText(/ожидает согласования/)).not.toBeInTheDocument()
  })

  it('переходит на /approvals при клике на кнопку', async () => {
    vi.mocked(dashboardHooks.useDashboardSummary).mockReturnValue({
      data: mockSummary,
      isLoading: false,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as ReturnType<typeof dashboardHooks.useDashboardSummary>)

    renderComponent(createAuthContext('security'))

    const user = userEvent.setup()
    await user.click(screen.getByText('Перейти'))

    expect(mockNavigate).toHaveBeenCalledWith('/approvals')
  })

  it('правильно склоняет "маршрут" для 1', () => {
    vi.mocked(dashboardHooks.useDashboardSummary).mockReturnValue({
      data: { ...mockSummary, pendingApprovalsCount: 1 },
      isLoading: false,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as ReturnType<typeof dashboardHooks.useDashboardSummary>)

    renderComponent(createAuthContext('security'))

    expect(screen.getByText(/1 маршрут ожидает согласования/)).toBeInTheDocument()
  })

  it('правильно склоняет "маршрута" для 2-4', () => {
    vi.mocked(dashboardHooks.useDashboardSummary).mockReturnValue({
      data: { ...mockSummary, pendingApprovalsCount: 3 },
      isLoading: false,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as ReturnType<typeof dashboardHooks.useDashboardSummary>)

    renderComponent(createAuthContext('security'))

    expect(screen.getByText(/3 маршрута ожидает согласования/)).toBeInTheDocument()
  })

  it('правильно склоняет "маршрутов" для 11-14', () => {
    vi.mocked(dashboardHooks.useDashboardSummary).mockReturnValue({
      data: { ...mockSummary, pendingApprovalsCount: 12 },
      isLoading: false,
      isError: false,
      error: null,
      refresh: vi.fn(),
    } as ReturnType<typeof dashboardHooks.useDashboardSummary>)

    renderComponent(createAuthContext('security'))

    expect(screen.getByText(/12 маршрутов ожидает согласования/)).toBeInTheDocument()
  })
})
