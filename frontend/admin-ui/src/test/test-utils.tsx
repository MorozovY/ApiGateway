// Утилиты для тестирования компонентов
import { render, type RenderOptions } from '@testing-library/react'
import { BrowserRouter, MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ConfigProvider } from 'antd'
import { AuthProvider, AuthContext } from '@features/auth'
import type { AuthContextType } from '@features/auth'
import type { ReactElement, ReactNode } from 'react'

// Создание тестового QueryClient
function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  })
}

// Провайдеры для тестов с реальным AuthProvider
interface AllProvidersProps {
  children: ReactNode
}

function AllProviders({ children }: AllProvidersProps) {
  const queryClient = createTestQueryClient()

  return (
    <QueryClientProvider client={queryClient}>
      <ConfigProvider>
        <BrowserRouter>
          <AuthProvider>{children}</AuthProvider>
        </BrowserRouter>
      </ConfigProvider>
    </QueryClientProvider>
  )
}

// Кастомный render с провайдерами
function customRender(
  ui: ReactElement,
  options?: Omit<RenderOptions, 'wrapper'>
) {
  return render(ui, { wrapper: AllProviders, ...options })
}

// Мок AuthContext для изолированных тестов
interface MockAuthProviderProps {
  children: ReactNode
  value?: Partial<AuthContextType>
}

export function MockAuthProvider({ children, value = {} }: MockAuthProviderProps) {
  const defaultValue: AuthContextType = {
    user: null,
    isAuthenticated: false,
    isLoading: false,
    error: null,
    login: async () => {},
    logout: async () => {},
    clearError: () => {},
    ...value,
  }

  return (
    <AuthContext.Provider value={defaultValue}>{children}</AuthContext.Provider>
  )
}

// Render с мок AuthContext
interface RenderWithMockAuthOptions extends Omit<RenderOptions, 'wrapper'> {
  authValue?: Partial<AuthContextType>
  initialEntries?: string[]
}

export function renderWithMockAuth(
  ui: ReactElement,
  { authValue, initialEntries = ['/'], ...options }: RenderWithMockAuthOptions = {}
) {
  const queryClient = createTestQueryClient()

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <ConfigProvider>
          <MemoryRouter initialEntries={initialEntries}>
            <MockAuthProvider value={authValue}>{children}</MockAuthProvider>
          </MemoryRouter>
        </ConfigProvider>
      </QueryClientProvider>
    )
  }

  return render(ui, { wrapper: Wrapper, ...options })
}

// Реэкспорт всех утилит testing-library
export * from '@testing-library/react'
export { customRender as render }
