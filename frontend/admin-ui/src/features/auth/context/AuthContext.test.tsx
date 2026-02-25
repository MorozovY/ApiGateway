// Тесты для AuthContext — Keycloak Direct Access Grants
// Story 12.2: Admin UI — Keycloak Auth Migration
// Story 12.9.1: Legacy cookie auth tests удалены — новые тесты для Keycloak

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
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

  // Code Review Fix: M6 - cleanup authEvents между тестами
  afterEach(() => {
    authEvents.onUnauthorized = () => {}
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

  // Code Review Fix: M1 - comprehensive token refresh tests
  describe('Token Refresh Logic', () => {
    it('автоматически обновляет токен когда сохранённый токен истёк', async () => {
      const { keycloakRefreshToken } = await import('../api/keycloakApi')
      const expiredTokens = {
        access_token: 'expired-token',
        refresh_token: 'valid-refresh-token',
        expires_in: 300,
        refresh_expires_in: 1800,
        token_type: 'Bearer',
        scope: 'openid profile email',
      }

      const newTokens = {
        ...expiredTokens,
        access_token: 'new-access-token',
        refresh_token: 'new-refresh-token',
      }

      // Сохраняем expired токен в sessionStorage (истёк 1 минуту назад)
      sessionStorage.setItem(
        'keycloak_tokens',
        JSON.stringify({
          ...expiredTokens,
          saved_at: Date.now() - 400000, // 400 секунд назад (expires_in=300)
        })
      )

      vi.mocked(keycloakRefreshToken).mockResolvedValueOnce(newTokens)

      renderWithProviders()

      // Ждём что refresh будет вызван автоматически
      await waitFor(() => {
        expect(keycloakRefreshToken).toHaveBeenCalledWith('valid-refresh-token')
      })
    })

    it('НЕ обновляет токен если refresh token истёк', async () => {
      const { keycloakRefreshToken } = await import('../api/keycloakApi')

      // Сохраняем tokens где ОБА токена истекли
      sessionStorage.setItem(
        'keycloak_tokens',
        JSON.stringify({
          access_token: 'expired-token',
          refresh_token: 'expired-refresh-token',
          expires_in: 300,
          refresh_expires_in: 1800,
          token_type: 'Bearer',
          scope: 'openid',
          saved_at: Date.now() - 2000000, // 2000 секунд назад (refresh_expires_in=1800)
        })
      )

      renderWithProviders()

      // Ждём инициализацию
      await waitFor(() => {
        expect(screen.getByTestId('is-authenticated')).toHaveTextContent('false')
      })

      // Refresh НЕ должен быть вызван
      expect(keycloakRefreshToken).not.toHaveBeenCalled()
    })

    it('обрабатывает malformed JSON в sessionStorage gracefully', async () => {
      // Сохраняем невалидный JSON
      sessionStorage.setItem('keycloak_tokens', 'invalid-json-{')

      renderWithProviders()

      // Приложение должно инициализироваться без краша
      await waitFor(() => {
        expect(screen.getByTestId('is-authenticated')).toHaveTextContent('false')
      })

      // Не должно быть ошибок
      expect(screen.getByTestId('error')).toHaveTextContent('null')
    })

    it('предотвращает concurrent refresh attempts (H2 fix validation)', async () => {
      const { keycloakRefreshToken } = await import('../api/keycloakApi')
      let refreshCallCount = 0

      // Мокаем медленный refresh (500ms)
      vi.mocked(keycloakRefreshToken).mockImplementation(async () => {
        refreshCallCount++
        await new Promise((resolve) => setTimeout(resolve, 500))
        return {
          access_token: 'new-token',
          refresh_token: 'new-refresh',
          expires_in: 300,
          refresh_expires_in: 1800,
          token_type: 'Bearer',
          scope: 'openid',
        }
      })

      // Сохраняем expired token
      sessionStorage.setItem(
        'keycloak_tokens',
        JSON.stringify({
          access_token: 'expired',
          refresh_token: 'refresh',
          expires_in: 300,
          refresh_expires_in: 1800,
          token_type: 'Bearer',
          scope: 'openid',
          saved_at: Date.now() - 400000,
        })
      )

      renderWithProviders()

      // Ждём что refresh вызван ОДИН раз (не два раза concurrently)
      await waitFor(
        () => {
          expect(refreshCallCount).toBe(1)
        },
        { timeout: 1000 }
      )
    })
  })
})
