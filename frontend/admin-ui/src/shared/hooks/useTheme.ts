// Hook для управления темой приложения (light/dark)
// При первом визите используется системная тема (prefers-color-scheme)
// Тема сохраняется в localStorage только когда пользователь явно её выбирает
import { useState, useEffect, useCallback, useRef } from 'react'

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

// Проверяем был ли уже явный выбор пользователя (есть тема в localStorage)
function hasUserSelectedTheme(): boolean {
  return getStoredTheme() !== null
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
  // Флаг: пользователь явно выбрал тему (через toggle/setTheme)
  // Инициализируется true если есть сохранённая тема в localStorage
  const isUserSelectedRef = useRef<boolean>(hasUserSelectedTheme())

  // Применяем тему к документу (всегда)
  // Сохранение в localStorage происходит только в toggle/setTheme (синхронно)
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme)
    document.documentElement.style.colorScheme = theme
  }, [theme])

  // Слушаем изменения системной темы (если пользователь не выбрал явно)
  useEffect(() => {
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)')

    const handleChange = (e: MediaQueryListEvent) => {
      // Обновляем только если нет явного выбора пользователя
      if (!isUserSelectedRef.current) {
        setThemeState(e.matches ? 'dark' : 'light')
      }
    }

    mediaQuery.addEventListener('change', handleChange)
    return () => mediaQuery.removeEventListener('change', handleChange)
  }, [])

  // Toggle помечает как явный выбор и сохраняет
  const toggle = useCallback(() => {
    isUserSelectedRef.current = true
    setThemeState(prev => {
      const newTheme = prev === 'light' ? 'dark' : 'light'
      // Сохраняем сразу при toggle (синхронно)
      localStorage.setItem(STORAGE_KEY, newTheme)
      return newTheme
    })
  }, [])

  // setTheme помечает как явный выбор и сохраняет
  const setTheme = useCallback((newTheme: Theme) => {
    isUserSelectedRef.current = true
    // Сохраняем сразу при setTheme (синхронно)
    localStorage.setItem(STORAGE_KEY, newTheme)
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
