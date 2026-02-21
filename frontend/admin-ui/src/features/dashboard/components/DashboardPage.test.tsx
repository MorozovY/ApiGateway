// Тесты для DashboardPage (Story 8.2 — MetricsWidget перемещён на /metrics)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import type { ReactNode } from 'react'
import { DashboardPage } from './DashboardPage'
import { AuthContext } from '@features/auth'
import type { AuthContextType, User } from '@features/auth'

// Тестовые данные
const mockUser: User = {
  userId: 'user-1',
  username: 'testuser',
  role: 'admin',
}

// Мок AuthContext
const mockAuthContext: AuthContextType = {
  user: mockUser,
  isAuthenticated: true,
  isLoading: false,
  error: null,
  login: vi.fn(),
  logout: vi.fn(),
  clearError: vi.fn(),
}

// Wrapper с QueryClient и AuthContext
function createWrapper(authContext: AuthContextType = mockAuthContext) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <AuthContext.Provider value={authContext}>
          <BrowserRouter>{children}</BrowserRouter>
        </AuthContext.Provider>
      </QueryClientProvider>
    )
  }
}

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('отображает приветствие с username', () => {
    render(<DashboardPage />, { wrapper: createWrapper() })

    expect(screen.getByText(/testuser/)).toBeInTheDocument()
    expect(screen.getByText('Dashboard')).toBeInTheDocument()
  })

  it('отображает роль пользователя', () => {
    render(<DashboardPage />, { wrapper: createWrapper() })

    expect(screen.getByText(/Admin/)).toBeInTheDocument()
  })

  it('отображает кнопку Logout', () => {
    render(<DashboardPage />, { wrapper: createWrapper() })

    expect(screen.getByTestId('logout-button')).toBeInTheDocument()
  })

  it('отображает роль Developer для developer пользователя', () => {
    const developerAuth: AuthContextType = {
      ...mockAuthContext,
      user: { ...mockUser, role: 'developer' },
    }

    render(<DashboardPage />, { wrapper: createWrapper(developerAuth) })

    expect(screen.getByText(/Developer/)).toBeInTheDocument()
  })

  it('НЕ отображает MetricsWidget на dashboard (AC1, Story 8.2)', () => {
    render(<DashboardPage />, { wrapper: createWrapper() })

    // MetricsWidget убран с Dashboard — метрики доступны на /metrics
    expect(screen.queryByTestId('metrics-widget')).not.toBeInTheDocument()
    expect(screen.queryByTestId('metrics-loading')).not.toBeInTheDocument()
  })
})
