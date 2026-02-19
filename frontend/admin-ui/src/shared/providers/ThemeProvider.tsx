// Провайдер темы для доступа к состоянию темы через context
import { createContext, useContext, ReactNode } from 'react'
import { useTheme, UseThemeResult } from '../hooks/useTheme'

const ThemeContext = createContext<UseThemeResult | null>(null)

interface ThemeProviderProps {
  children: ReactNode
}

export function ThemeProvider({ children }: ThemeProviderProps) {
  const themeValue = useTheme()

  return (
    <ThemeContext.Provider value={themeValue}>
      {children}
    </ThemeContext.Provider>
  )
}

// Hook для использования темы из context
export function useThemeContext(): UseThemeResult {
  const context = useContext(ThemeContext)
  if (!context) {
    throw new Error('useThemeContext must be used within ThemeProvider')
  }
  return context
}
