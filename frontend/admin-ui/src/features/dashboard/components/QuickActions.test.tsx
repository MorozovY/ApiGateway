/**
 * Тесты для QuickActions компонента (Story 16.2)
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { QuickActions } from './QuickActions'
import { AuthContext } from '@features/auth'
import type { AuthContextType, User } from '@features/auth'

// Мокаем useNavigate
const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

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

describe('QuickActions', () => {
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
            <QuickActions />
          </MemoryRouter>
        </AuthContext.Provider>
      </QueryClientProvider>,
    )
  }

  describe('AC1: Developer Quick Actions', () => {
    it('отображает кнопку "Создать маршрут" для developer', () => {
      renderComponent(createAuthContext('developer'))

      expect(screen.getByText('Создать маршрут')).toBeInTheDocument()
    })

    it('отображает кнопку "Мои маршруты" для developer', () => {
      renderComponent(createAuthContext('developer'))

      expect(screen.getByText('Мои маршруты')).toBeInTheDocument()
    })

    it('переходит на /routes/new при клике на "Создать маршрут"', async () => {
      renderComponent(createAuthContext('developer'))

      const user = userEvent.setup()
      await user.click(screen.getByText('Создать маршрут'))

      expect(mockNavigate).toHaveBeenCalledWith('/routes/new')
    })

    it('переходит на /routes при клике на "Мои маршруты"', async () => {
      renderComponent(createAuthContext('developer'))

      const user = userEvent.setup()
      await user.click(screen.getByText('Мои маршруты'))

      expect(mockNavigate).toHaveBeenCalledWith('/routes')
    })
  })

  describe('AC2: Security Quick Actions', () => {
    it('отображает кнопку "Согласования" для security', () => {
      renderComponent(createAuthContext('security'))

      expect(screen.getByText('Согласования')).toBeInTheDocument()
    })

    it('отображает кнопку "Журнал аудита" для security', () => {
      renderComponent(createAuthContext('security'))

      expect(screen.getByText('Журнал аудита')).toBeInTheDocument()
    })

    it('переходит на /approvals при клике на "Согласования"', async () => {
      renderComponent(createAuthContext('security'))

      const user = userEvent.setup()
      await user.click(screen.getByText('Согласования'))

      expect(mockNavigate).toHaveBeenCalledWith('/approvals')
    })

    it('переходит на /audit при клике на "Журнал аудита"', async () => {
      renderComponent(createAuthContext('security'))

      const user = userEvent.setup()
      await user.click(screen.getByText('Журнал аудита'))

      expect(mockNavigate).toHaveBeenCalledWith('/audit')
    })
  })

  describe('AC3: Admin Quick Actions', () => {
    it('отображает все основные действия для admin', () => {
      renderComponent(createAuthContext('admin'))

      expect(screen.getByText('Создать маршрут')).toBeInTheDocument()
      expect(screen.getByText('Согласования')).toBeInTheDocument()
      expect(screen.getByText('Пользователи')).toBeInTheDocument()
      expect(screen.getByText('Потребители')).toBeInTheDocument()
      expect(screen.getByText('Метрики')).toBeInTheDocument()
      expect(screen.getByText('Лимиты')).toBeInTheDocument()
    })

    it('переходит на /users при клике на "Пользователи"', async () => {
      renderComponent(createAuthContext('admin'))

      const user = userEvent.setup()
      await user.click(screen.getByText('Пользователи'))

      expect(mockNavigate).toHaveBeenCalledWith('/users')
    })
  })
})
