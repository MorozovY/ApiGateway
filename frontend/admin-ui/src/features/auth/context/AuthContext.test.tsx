// Тесты для AuthContext
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor, act } from '@testing-library/react'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from 'antd'
import { AuthProvider } from './AuthContext'
import { useAuth } from '../hooks/useAuth'
import * as authApi from '../api/authApi'

// Мок API модуля
vi.mock('../api/authApi', () => ({
  loginApi: vi.fn(),
  logoutApi: vi.fn(),
}))

// Компонент для тестирования AuthContext
function TestConsumer() {
  const { user, isAuthenticated, isLoading, error, login, logout, clearError } = useAuth()
  const location = useLocation()

  return (
    <div>
      <div data-testid="is-authenticated">{String(isAuthenticated)}</div>
      <div data-testid="is-loading">{String(isLoading)}</div>
      <div data-testid="error">{error ?? 'null'}</div>
      <div data-testid="username">{user?.username ?? 'null'}</div>
      <div data-testid="role">{user?.role ?? 'null'}</div>
      <div data-testid="location">{location.pathname}</div>
      <button data-testid="login-btn" onClick={() => login('testuser', 'password')}>
        Login
      </button>
      <button data-testid="logout-btn" onClick={() => logout()}>
        Logout
      </button>
      <button data-testid="clear-error-btn" onClick={clearError}>
        Clear Error
      </button>
    </div>
  )
}

// Тип для начальной записи с state
interface InitialEntry {
  pathname: string
  state?: Record<string, unknown>
}

// Хелпер для рендеринга с провайдерами
function renderWithProviders(initialEntries: (string | InitialEntry)[] = ['/']) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <ConfigProvider>
        <MemoryRouter initialEntries={initialEntries}>
          <AuthProvider>
            <Routes>
              <Route path="*" element={<TestConsumer />} />
            </Routes>
          </AuthProvider>
        </MemoryRouter>
      </ConfigProvider>
    </QueryClientProvider>
  )
}

describe('AuthContext', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('инициализируется с пустым состоянием', () => {
    renderWithProviders()

    expect(screen.getByTestId('is-authenticated')).toHaveTextContent('false')
    expect(screen.getByTestId('is-loading')).toHaveTextContent('false')
    expect(screen.getByTestId('error')).toHaveTextContent('null')
    expect(screen.getByTestId('username')).toHaveTextContent('null')
  })

  it('устанавливает user при успешном login', async () => {
    const mockUser = { userId: '1', username: 'testuser', role: 'developer' as const }
    vi.mocked(authApi.loginApi).mockResolvedValueOnce(mockUser)

    renderWithProviders()

    const user = userEvent.setup()
    await user.click(screen.getByTestId('login-btn'))

    await waitFor(() => {
      expect(screen.getByTestId('is-authenticated')).toHaveTextContent('true')
    })
    expect(screen.getByTestId('username')).toHaveTextContent('testuser')
    expect(screen.getByTestId('role')).toHaveTextContent('developer')
  })

  it('устанавливает isLoading в true во время login', async () => {
    // Создаём промис, который мы можем контролировать
    let resolveLogin: (value: any) => void
    const loginPromise = new Promise((resolve) => {
      resolveLogin = resolve
    })
    vi.mocked(authApi.loginApi).mockReturnValueOnce(loginPromise as any)

    renderWithProviders()

    // Начинаем логин (используем act для синхронизации)
    await act(async () => {
      screen.getByTestId('login-btn').click()
    })

    // Проверяем loading state
    expect(screen.getByTestId('is-loading')).toHaveTextContent('true')

    // Завершаем логин
    await act(async () => {
      resolveLogin!({ userId: '1', username: 'test', role: 'developer' })
    })

    await waitFor(() => {
      expect(screen.getByTestId('is-loading')).toHaveTextContent('false')
    })
  })

  it('устанавливает error при неудачном login', async () => {
    vi.mocked(authApi.loginApi).mockRejectedValueOnce(new Error('Invalid credentials'))

    renderWithProviders()

    const user = userEvent.setup()
    await user.click(screen.getByTestId('login-btn'))

    await waitFor(() => {
      expect(screen.getByTestId('error')).toHaveTextContent('Invalid credentials')
    })
    expect(screen.getByTestId('is-authenticated')).toHaveTextContent('false')
  })

  it('очищает error через clearError', async () => {
    vi.mocked(authApi.loginApi).mockRejectedValueOnce(new Error('Invalid credentials'))

    renderWithProviders()

    const user = userEvent.setup()

    // Сначала вызываем ошибку
    await user.click(screen.getByTestId('login-btn'))

    await waitFor(() => {
      expect(screen.getByTestId('error')).toHaveTextContent('Invalid credentials')
    })

    // Очищаем ошибку
    await user.click(screen.getByTestId('clear-error-btn'))

    expect(screen.getByTestId('error')).toHaveTextContent('null')
  })

  it('очищает user при logout', async () => {
    const mockUser = { userId: '1', username: 'testuser', role: 'developer' as const }
    vi.mocked(authApi.loginApi).mockResolvedValueOnce(mockUser)
    vi.mocked(authApi.logoutApi).mockResolvedValueOnce(undefined)

    renderWithProviders()

    const user = userEvent.setup()

    // Сначала логинимся
    await user.click(screen.getByTestId('login-btn'))

    await waitFor(() => {
      expect(screen.getByTestId('is-authenticated')).toHaveTextContent('true')
    })

    // Выполняем logout
    await user.click(screen.getByTestId('logout-btn'))

    await waitFor(() => {
      expect(screen.getByTestId('is-authenticated')).toHaveTextContent('false')
    })
    expect(screen.getByTestId('username')).toHaveTextContent('null')
  })

  it('редиректит на /dashboard после успешного login', async () => {
    const mockUser = { userId: '1', username: 'testuser', role: 'developer' as const }
    vi.mocked(authApi.loginApi).mockResolvedValueOnce(mockUser)

    renderWithProviders(['/login'])

    const user = userEvent.setup()
    await user.click(screen.getByTestId('login-btn'))

    await waitFor(() => {
      expect(screen.getByTestId('location')).toHaveTextContent('/dashboard')
    })
  })

  it('редиректит на returnUrl после успешного login (AC5)', async () => {
    const mockUser = { userId: '1', username: 'testuser', role: 'developer' as const }
    vi.mocked(authApi.loginApi).mockResolvedValueOnce(mockUser)

    // Симулируем ситуацию: пользователь был перенаправлен на /login с /routes
    renderWithProviders([{ pathname: '/login', state: { returnUrl: '/routes' } }])

    const user = userEvent.setup()
    await user.click(screen.getByTestId('login-btn'))

    // После логина должен быть редирект на /routes (изначально запрошенный route)
    await waitFor(() => {
      expect(screen.getByTestId('location')).toHaveTextContent('/routes')
    })
  })

  it('редиректит на /login после logout', async () => {
    const mockUser = { userId: '1', username: 'testuser', role: 'developer' as const }
    vi.mocked(authApi.loginApi).mockResolvedValueOnce(mockUser)
    vi.mocked(authApi.logoutApi).mockResolvedValueOnce(undefined)

    renderWithProviders(['/dashboard'])

    const user = userEvent.setup()

    // Логинимся
    await user.click(screen.getByTestId('login-btn'))
    await waitFor(() => {
      expect(screen.getByTestId('is-authenticated')).toHaveTextContent('true')
    })

    // Логаут
    await user.click(screen.getByTestId('logout-btn'))

    await waitFor(() => {
      expect(screen.getByTestId('location')).toHaveTextContent('/login')
    })
  })

  it('очищает user при logout даже если API вернул ошибку', async () => {
    const mockUser = { userId: '1', username: 'testuser', role: 'developer' as const }
    vi.mocked(authApi.loginApi).mockResolvedValueOnce(mockUser)
    // Создаём rejected promise, который будет перехвачен в try/finally
    const logoutError = new Error('Server error')
    vi.mocked(authApi.logoutApi).mockImplementationOnce(() => Promise.reject(logoutError))

    renderWithProviders(['/dashboard'])

    const user = userEvent.setup()

    // Логинимся
    await user.click(screen.getByTestId('login-btn'))
    await waitFor(() => {
      expect(screen.getByTestId('is-authenticated')).toHaveTextContent('true')
    })

    // Логаут (API вернёт ошибку, но user должен очиститься благодаря try/finally)
    await act(async () => {
      await user.click(screen.getByTestId('logout-btn'))
    })

    await waitFor(() => {
      expect(screen.getByTestId('is-authenticated')).toHaveTextContent('false')
    })
    expect(screen.getByTestId('username')).toHaveTextContent('null')
    expect(screen.getByTestId('location')).toHaveTextContent('/login')
  })
})
