// Hook для доступа к AuthContext
import { useContext } from 'react'
import { AuthContext } from '../context/AuthContext'
import type { AuthContextType } from '../types/auth.types'

/**
 * Hook для доступа к контексту аутентификации
 * Должен использоваться внутри AuthProvider
 */
export function useAuth(): AuthContextType {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return context
}
