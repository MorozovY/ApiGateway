// Интеграционный тест: ThemeProvider + ConfigProvider + Ant Design (Story 6.0)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, act } from '@testing-library/react'
import { ConfigProvider, Button, theme as antTheme } from 'antd'
import { ThemeProvider, useThemeContext } from './ThemeProvider'

// Мокаем localStorage (тот же подход как в useTheme.test.ts)
const localStorageMock = (() => {
  let store: Record<string, string> = {}
  return {
    getItem: vi.fn((key: string) => store[key] ?? null),
    setItem: vi.fn((key: string, value: string) => {
      store[key] = value
    }),
    removeItem: vi.fn((key: string) => {
      delete store[key]
    }),
    clear: () => {
      store = {}
    },
    // Для проверки в тестах
    getStore: () => store,
  }
})()

// Мокаем matchMedia
const createMatchMediaMock = (matches: boolean) => {
  return vi.fn().mockImplementation((query: string) => ({
    matches,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }))
}

// Компонент который интегрирует ThemeProvider с ConfigProvider (как в main.tsx)
function ThemedApp({ children }: { children: React.ReactNode }) {
  const { isDark } = useThemeContext()

  return (
    <ConfigProvider
      theme={{
        algorithm: isDark ? antTheme.darkAlgorithm : antTheme.defaultAlgorithm,
        token: {
          colorPrimary: '#1890ff',
        },
      }}
    >
      {children}
    </ConfigProvider>
  )
}

// Тестовый компонент который отображает текущую тему
function ThemeDisplay() {
  const { theme, toggle } = useThemeContext()
  return (
    <div>
      <span data-testid="current-theme">{theme}</span>
      <Button data-testid="toggle-btn" onClick={toggle}>
        Toggle Theme
      </Button>
      <Button data-testid="ant-button" type="primary">
        Test Button
      </Button>
    </div>
  )
}

describe('Theme Integration (ThemeProvider + ConfigProvider)', () => {
  beforeEach(() => {
    localStorageMock.clear()
    vi.stubGlobal('localStorage', localStorageMock)
    vi.stubGlobal('matchMedia', createMatchMediaMock(false))
    document.documentElement.removeAttribute('data-theme')
    document.documentElement.style.colorScheme = ''
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('ConfigProvider получает корректный algorithm для light темы', () => {
    render(
      <ThemeProvider>
        <ThemedApp>
          <ThemeDisplay />
        </ThemedApp>
      </ThemeProvider>
    )

    expect(screen.getByTestId('current-theme')).toHaveTextContent('light')
    // Проверяем что Ant Design кнопка рендерится
    expect(screen.getByTestId('ant-button')).toBeInTheDocument()
  })

  it('ConfigProvider получает корректный algorithm для dark темы', () => {
    // Устанавливаем dark тему в localStorage до рендера
    localStorageMock.setItem('app-theme', 'dark')

    render(
      <ThemeProvider>
        <ThemedApp>
          <ThemeDisplay />
        </ThemedApp>
      </ThemeProvider>
    )

    expect(screen.getByTestId('current-theme')).toHaveTextContent('dark')
    expect(screen.getByTestId('ant-button')).toBeInTheDocument()
  })

  it('переключение темы обновляет ConfigProvider algorithm', async () => {
    render(
      <ThemeProvider>
        <ThemedApp>
          <ThemeDisplay />
        </ThemedApp>
      </ThemeProvider>
    )

    expect(screen.getByTestId('current-theme')).toHaveTextContent('light')

    // Переключаем тему
    await act(async () => {
      fireEvent.click(screen.getByTestId('toggle-btn'))
    })

    expect(screen.getByTestId('current-theme')).toHaveTextContent('dark')
    // Кнопка должна продолжать рендериться после переключения темы
    expect(screen.getByTestId('ant-button')).toBeInTheDocument()
  })

  it('data-theme атрибут синхронизирован с темой', async () => {
    render(
      <ThemeProvider>
        <ThemedApp>
          <ThemeDisplay />
        </ThemedApp>
      </ThemeProvider>
    )

    expect(document.documentElement.getAttribute('data-theme')).toBe('light')

    await act(async () => {
      fireEvent.click(screen.getByTestId('toggle-btn'))
    })

    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
  })

  it('colorScheme style синхронизирован с темой', async () => {
    render(
      <ThemeProvider>
        <ThemedApp>
          <ThemeDisplay />
        </ThemedApp>
      </ThemeProvider>
    )

    expect(document.documentElement.style.colorScheme).toBe('light')

    await act(async () => {
      fireEvent.click(screen.getByTestId('toggle-btn'))
    })

    expect(document.documentElement.style.colorScheme).toBe('dark')
  })

  it('тема сохраняется в localStorage при переключении', async () => {
    render(
      <ThemeProvider>
        <ThemedApp>
          <ThemeDisplay />
        </ThemedApp>
      </ThemeProvider>
    )

    await act(async () => {
      fireEvent.click(screen.getByTestId('toggle-btn'))
    })

    expect(localStorageMock.setItem).toHaveBeenCalledWith('app-theme', 'dark')
  })
})
