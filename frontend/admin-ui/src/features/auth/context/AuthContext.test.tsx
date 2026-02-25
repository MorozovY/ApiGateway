// Тесты для AuthContext — Keycloak Direct Access Grants
// Story 12.2: Admin UI — Keycloak Auth Migration
// Story 12.9.1: Legacy cookie auth tests удалены — новые тесты для Keycloak

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor, act } from '@testing-library/react'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from 'antd'
import { AuthProvider } from './AuthContext'
import { useAuth } from '../hooks/useAuth'
import { authEvents } from '@shared/utils/axios'

// Мок Keycloak API
vi.mock('../api/keycloakApi', () => ({
  keycloakLogin: vi.fn(),
  keycloakLogout: vi.fn(),
  keycloakRefreshToken: vi.fn(),
}))

// Мок oidcConfig
vi.mock('../config/oidcConfig', () => ({
  mapKeycloakRoles: vi.fn((roles: string[]) => {
    if (roles.includes('admin-ui:admin')) return 'admin'
    if (roles.includes('admin-ui:security')) return 'security'
    return 'developer'
  }),
  extractKeycloakRoles: vi.fn(() => ['admin-ui:developer']),
  decodeJwtPayload: vi.fn(() => ({
    sub: 'user-123',
    preferred_username: 'testuser',
    email: 'test@example.com',
  })),
}))

// Мок axios authEvents
vi.mock('@shared/utils/axios', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
  authEvents: {
    onUnauthorized: vi.fn(),
  },
  setTokenGetter: vi.fn(),
}))

// Компонент для тестирования AuthContext
function TestConsumer() {
  const { user, isAuthenticated, isLoading, error, login, logout, clearError } = useAuth()

  return (
    <div>
      <div data-testid="is-authenticated">{String(isAuthenticated)}</div>
      <div data-testid="is-loading">{String(isLoading)}</div>
      <div data-testid="error">{error ?? 'null'}</div>
      <div data-testid="username">{user?.username ?? 'null'}</div>
      <div data-testid="role">{user?.role ?? 'null'}</div>
      <button onClick={() => login('testuser', 'password')}>Login</button>
      <button onClick={logout}>Logout</button>
      <button onClick={clearError}>Clear Error</button>
    </div>
  )
}

describe('AuthContext', () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  })

  beforeEach(() => {
    vi.clearAllMocks()
    sessionStorage.clear()
  })

  function renderWithProviders() {
    return render(
      <ConfigProvider>
        <QueryClientProvider client={queryClient}>
          <MemoryRouter initialEntries={['/']}>
            <AuthProvider>
              <Routes>
                <Route path="/" element={<TestConsumer />} />
                <Route path="/login" element={<div>Login Page</div>} />
                <Route path="/dashboard" element={<div>Dashboard</div>} />
              </Routes>
            </AuthProvider>
          </MemoryRouter>
        </QueryClientProvider>
      </ConfigProvider>
    )
  }

  it('инициализируется с пустым состоянием', async () => {
    renderWithProviders()

    await waitFor(() => {
      expect(screen.getByTestId('is-authenticated')).toHaveTextContent('false')
    })

    expect(screen.getByTestId('is-loading')).toHaveTextContent('false')
    expect(screen.getByTestId('error')).toHaveTextContent('null')
    expect(screen.getByTestId('username')).toHaveTextContent('null')
  })

  it('устанавливает user при успешном login', async () => {
    const { keycloakLogin } = await import('../api/keycloakApi')
    const mockTokens = {
      access_token: 'mock-access-token',
      refresh_token: 'mock-refresh-token',
      expires_in: 300,
      refresh_expires_in: 1800,
      token_type: 'Bearer',
      scope: 'openid profile email',
    }

    vi.mocked(keycloakLogin).mockResolvedValueOnce(mockTokens)

    renderWithProviders()

    await waitFor(() => {
      expect(screen.getByTestId('is-authenticated')).toHaveTextContent('false')
    })

    const loginButton = screen.getByText('Login')
    await act(async () => {
      await userEvent.click(loginButton)
    })

    // После успешного login происходит редирект на /dashboard
    // Проверяем что keycloakLogin был вызван
    await waitFor(() => {
      expect(keycloakLogin).toHaveBeenCalledWith('testuser', 'password')
    })
  })

  it('устанавливает error при неудачном login', async () => {
    const { keycloakLogin } = await import('../api/keycloakApi')
    vi.mocked(keycloakLogin).mockRejectedValueOnce(new Error('Неверные учётные данные'))

    renderWithProviders()

    await waitFor(() => {
      expect(screen.getByTestId('is-authenticated')).toHaveTextContent('false')
    })

    const loginButton = screen.getByText('Login')
    await act(async () => {
      await userEvent.click(loginButton)
    })

    await waitFor(() => {
      expect(screen.getByTestId('error')).toHaveTextContent('Неверные учётные данные')
    })

    expect(screen.getByTestId('is-authenticated')).toHaveTextContent('false')
  })
})
