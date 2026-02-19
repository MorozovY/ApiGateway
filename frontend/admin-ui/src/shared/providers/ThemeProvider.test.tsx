// Тесты для ThemeProvider (Story 6.0 — Theme Switcher)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, renderHook, act } from '@testing-library/react'
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

describe('ThemeProvider', () => {
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

  it('предоставляет theme context дочерним компонентам', () => {
    function TestComponent() {
      const { theme, isDark, isLight } = useThemeContext()
      return (
        <div data-testid="theme-info">
          {theme} | isDark: {String(isDark)} | isLight: {String(isLight)}
        </div>
      )
    }

    render(
      <ThemeProvider>
        <TestComponent />
      </ThemeProvider>
    )

    expect(screen.getByTestId('theme-info')).toHaveTextContent('light | isDark: false | isLight: true')
  })

  it('предоставляет toggle функцию через context', async () => {
    function TestComponent() {
      const { theme, toggle } = useThemeContext()
      return (
        <div>
          <span data-testid="theme">{theme}</span>
          <button onClick={toggle}>Toggle</button>
        </div>
      )
    }

    render(
      <ThemeProvider>
        <TestComponent />
      </ThemeProvider>
    )

    expect(screen.getByTestId('theme')).toHaveTextContent('light')

    await act(async () => {
      screen.getByRole('button').click()
    })

    expect(screen.getByTestId('theme')).toHaveTextContent('dark')
  })

  it('предоставляет setTheme функцию через context', async () => {
    function TestComponent() {
      const { theme, setTheme } = useThemeContext()
      return (
        <div>
          <span data-testid="theme">{theme}</span>
          <button onClick={() => setTheme('dark')}>Set Dark</button>
        </div>
      )
    }

    render(
      <ThemeProvider>
        <TestComponent />
      </ThemeProvider>
    )

    expect(screen.getByTestId('theme')).toHaveTextContent('light')

    await act(async () => {
      screen.getByRole('button').click()
    })

    expect(screen.getByTestId('theme')).toHaveTextContent('dark')
  })
})

describe('useThemeContext', () => {
  beforeEach(() => {
    localStorageMock.clear()
    vi.stubGlobal('localStorage', localStorageMock)
    vi.stubGlobal('matchMedia', createMatchMediaMock(false))
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('выбрасывает ошибку при использовании вне ThemeProvider', () => {
    // Подавляем console.error для чистоты вывода теста
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

    expect(() => {
      renderHook(() => useThemeContext())
    }).toThrow('useThemeContext must be used within ThemeProvider')

    consoleSpy.mockRestore()
  })

  it('не выбрасывает ошибку при использовании внутри ThemeProvider', () => {
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <ThemeProvider>{children}</ThemeProvider>
    )

    expect(() => {
      renderHook(() => useThemeContext(), { wrapper })
    }).not.toThrow()
  })

  it('возвращает все необходимые поля из context', () => {
    const wrapper = ({ children }: { children: React.ReactNode }) => (
      <ThemeProvider>{children}</ThemeProvider>
    )

    const { result } = renderHook(() => useThemeContext(), { wrapper })

    expect(result.current).toHaveProperty('theme')
    expect(result.current).toHaveProperty('isDark')
    expect(result.current).toHaveProperty('isLight')
    expect(result.current).toHaveProperty('toggle')
    expect(result.current).toHaveProperty('setTheme')
    expect(typeof result.current.toggle).toBe('function')
    expect(typeof result.current.setTheme).toBe('function')
  })
})
