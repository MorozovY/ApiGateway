// Тесты для ThemeSwitcher компонента (Story 6.0 — Theme Switcher)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { ThemeSwitcher } from './ThemeSwitcher'
import type { UseThemeResult } from '../hooks/useTheme'

// Мокаем useThemeContext
const mockToggle = vi.fn()
const mockThemeContext: UseThemeResult = {
  theme: 'light',
  isDark: false,
  isLight: true,
  toggle: mockToggle,
  setTheme: vi.fn(),
}

vi.mock('../providers/ThemeProvider', () => ({
  useThemeContext: () => mockThemeContext,
}))

describe('ThemeSwitcher', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Сброс к light теме
    mockThemeContext.theme = 'light'
    mockThemeContext.isDark = false
    mockThemeContext.isLight = true
  })

  it('рендерится корректно', () => {
    render(<ThemeSwitcher />)

    const button = screen.getByRole('button', { name: /переключить тему/i })
    expect(button).toBeInTheDocument()
  })

  it('показывает иконку луны когда тема light', () => {
    render(<ThemeSwitcher />)

    // В light теме показывается иконка луны (для переключения на dark)
    const button = screen.getByTestId('theme-switcher')
    expect(button).toBeInTheDocument()
    // MoonOutlined иконка должна быть внутри кнопки
    expect(button.querySelector('.anticon-moon')).toBeInTheDocument()
  })

  it('показывает иконку солнца когда тема dark', () => {
    mockThemeContext.theme = 'dark'
    mockThemeContext.isDark = true
    mockThemeContext.isLight = false

    render(<ThemeSwitcher />)

    // В dark теме показывается иконка солнца (для переключения на light)
    const button = screen.getByTestId('theme-switcher')
    expect(button.querySelector('.anticon-sun')).toBeInTheDocument()
  })

  it('вызывает toggle при клике', () => {
    render(<ThemeSwitcher />)

    const button = screen.getByTestId('theme-switcher')
    fireEvent.click(button)

    expect(mockToggle).toHaveBeenCalledTimes(1)
  })

  it('имеет data-testid для E2E тестов', () => {
    render(<ThemeSwitcher />)

    const button = screen.getByTestId('theme-switcher')
    expect(button).toBeInTheDocument()
  })

  it('имеет aria-label для доступности', () => {
    render(<ThemeSwitcher />)

    const button = screen.getByRole('button', { name: /переключить тему/i })
    expect(button).toHaveAttribute('aria-label', 'Переключить тему')
  })
})
