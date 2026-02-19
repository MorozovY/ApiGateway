// Hook для управления темой приложения (light/dark)
import { useState, useEffect, useCallback } from 'react'

export type Theme = 'light' | 'dark'

const STORAGE_KEY = 'app-theme'

// Получение системной темы через media query
function getSystemTheme(): Theme {
  if (typeof window === 'undefined') return 'light'
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

// Получение сохранённой темы из localStorage
function getStoredTheme(): Theme | null {
  if (typeof window === 'undefined') return null
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored === 'light' || stored === 'dark') {
    return stored
  }
  return null
}

// Инициализация темы: сохранённая → системная
function getInitialTheme(): Theme {
  return getStoredTheme() ?? getSystemTheme()
}

export interface UseThemeResult {
  theme: Theme
  isDark: boolean
  isLight: boolean
  toggle: () => void
  setTheme: (theme: Theme) => void
}

export function useTheme(): UseThemeResult {
  const [theme, setThemeState] = useState<Theme>(getInitialTheme)

  // Сохраняем тему в localStorage и обновляем data-theme атрибут
  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, theme)
    document.documentElement.setAttribute('data-theme', theme)

    // Обновляем colorScheme для корректной работы системных элементов
    document.documentElement.style.colorScheme = theme
  }, [theme])

  // Слушаем изменения системной темы (если пользователь не выбрал явно)
  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')

    const handleChange = (e: MediaQueryListEvent) => {
      // Обновляем только если нет сохранённой темы
      if (!getStoredTheme()) {
        setThemeState(e.matches ? 'dark' : 'light')
      }
    }

    mediaQuery.addEventListener('change', handleChange)
    return () => mediaQuery.removeEventListener('change', handleChange)
  }, [])

  const toggle = useCallback(() => {
    setThemeState(prev => prev === 'light' ? 'dark' : 'light')
  }, [])

  const setTheme = useCallback((newTheme: Theme) => {
    setThemeState(newTheme)
  }, [])

  return {
    theme,
    isDark: theme === 'dark',
    isLight: theme === 'light',
    toggle,
    setTheme,
  }
}
